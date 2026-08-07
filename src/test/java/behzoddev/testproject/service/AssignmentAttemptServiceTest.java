package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.AttemptAnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.QuestionSetItemRepository;
import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dto.student.AnswerSyncDto;
import behzoddev.testproject.dto.student.AttemptDto;
import behzoddev.testproject.dto.student.ResponseAssignmentsAndTaskStatusDto;
import behzoddev.testproject.dto.student.SyncAttemptRequestDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.AttemptAnswer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.TaskStatus;
import behzoddev.testproject.telegram.dao.AttemptQuestionOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Topshiriqni bajarish jarayoni — vaqt hisoblash (updateDuration, static
 * yordamchi metod sifatida hamma joyda qayta ishlatiladi) va status
 * aniqlash (NEW/IN_PROGRESS/FINISHED/OVERDUE) mantig'iga e'tibor qaratilgan.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentAttemptServiceTest {

    @Mock
    private AssignmentAttemptRepository assignmentAttemptRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private QuestionSetItemRepository questionSetItemRepository;
    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private AttemptHeartbeatService attemptHeartbeatService;
    @Mock
    private AttemptQuestionOrderRepository attemptQuestionOrderRepository;

    @InjectMocks
    private AssignmentAttemptService assignmentAttemptService;

    // ===== updateDuration (static, sof mantiq) =====

    @Test
    void updateDuration_notStartedYet_doesNothing() {
        AssignmentAttempt attempt = AssignmentAttempt.builder().durationSec(0).build();

        AssignmentAttemptService.updateDuration(attempt);

        assertThat(attempt.getDurationSec()).isZero();
        assertThat(attempt.getLastSync()).isNull();
    }

    @Test
    void updateDuration_alreadyFinished_doesNothing() {
        AssignmentAttempt attempt = AssignmentAttempt.builder()
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .finishedAt(LocalDateTime.now())
                .durationSec(100)
                .lastSync(LocalDateTime.now().minusMinutes(1))
                .build();
        LocalDateTime beforeSync = attempt.getLastSync();

        AssignmentAttemptService.updateDuration(attempt);

        assertThat(attempt.getDurationSec()).isEqualTo(100);
        assertThat(attempt.getLastSync()).isEqualTo(beforeSync);
    }

    @Test
    void updateDuration_noPreviousSync_justSetsLastSyncWithoutAddingDuration() {
        AssignmentAttempt attempt = AssignmentAttempt.builder()
                .startedAt(LocalDateTime.now().minusMinutes(1))
                .durationSec(0)
                .lastSync(null)
                .build();

        AssignmentAttemptService.updateDuration(attempt);

        assertThat(attempt.getDurationSec()).isZero();
        assertThat(attempt.getLastSync()).isNotNull();
    }

    @Test
    void updateDuration_tenSecondsSinceLastSync_addsTenSeconds() {
        AssignmentAttempt attempt = AssignmentAttempt.builder()
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .durationSec(50)
                .lastSync(LocalDateTime.now().minusSeconds(10))
                .build();

        AssignmentAttemptService.updateDuration(attempt);

        assertThat(attempt.getDurationSec()).isBetween(59, 61); // ~50+10, ozgina test ijro vaqti farqi bilan
    }

    @Test
    void updateDuration_longGapSinceLastSync_cappedAtFiveMinutes() {
        AssignmentAttempt attempt = AssignmentAttempt.builder()
                .startedAt(LocalDateTime.now().minusHours(2))
                .durationSec(0)
                .lastSync(LocalDateTime.now().minusHours(1)) // 3600 soniya oldin
                .build();

        AssignmentAttemptService.updateDuration(attempt);

        assertThat(attempt.getDurationSec()).isEqualTo(300); // maksimum 5 daqiqa
    }

    // ===== getTasksAndTaskStatus =====

    private Assignment assignmentWithDueDate(LocalDateTime dueDate) {
        User teacher = User.builder().id(50L).username("teacher").build();
        QuestionSet set = QuestionSet.builder().id(1L).teacher(teacher).name("Set1").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh1").build();
        return Assignment.builder().id(1L).questionSet(set).group(group).assignedBy(teacher)
                .assignedAt(LocalDateTime.now().minusDays(1)).dueDate(dueDate).build();
    }

    @Test
    void getTasksAndTaskStatus_noAttemptAndFutureDue_isNew() {
        User pupil = User.builder().id(1L).username("pupil").build();
        Assignment assignment = assignmentWithDueDate(LocalDateTime.now().plusDays(1));

        when(assignmentRepository.findAllByRecipientsPupil(pupil)).thenReturn(List.of(assignment));
        when(assignmentAttemptRepository.findAllByPupil(pupil)).thenReturn(List.of());

        List<ResponseAssignmentsAndTaskStatusDto> result = assignmentAttemptService.getTasksAndTaskStatus(pupil);

        assertThat(result.get(0).taskStatus()).isEqualTo(TaskStatus.NEW);
    }

    @Test
    void getTasksAndTaskStatus_noAttemptAndPastDue_isOverdue() {
        User pupil = User.builder().id(1L).username("pupil").build();
        Assignment assignment = assignmentWithDueDate(LocalDateTime.now().minusDays(1));

        when(assignmentRepository.findAllByRecipientsPupil(pupil)).thenReturn(List.of(assignment));
        when(assignmentAttemptRepository.findAllByPupil(pupil)).thenReturn(List.of());

        List<ResponseAssignmentsAndTaskStatusDto> result = assignmentAttemptService.getTasksAndTaskStatus(pupil);

        assertThat(result.get(0).taskStatus()).isEqualTo(TaskStatus.OVERDUE);
    }

    @Test
    void getTasksAndTaskStatus_finishedAttempt_isFinishedRegardlessOfDueDate() {
        User pupil = User.builder().id(1L).username("pupil").build();
        Assignment assignment = assignmentWithDueDate(LocalDateTime.now().minusDays(1));
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(9L).assignment(assignment).pupil(pupil)
                .finishedAt(LocalDateTime.now()).build();

        when(assignmentRepository.findAllByRecipientsPupil(pupil)).thenReturn(List.of(assignment));
        when(assignmentAttemptRepository.findAllByPupil(pupil)).thenReturn(List.of(attempt));

        List<ResponseAssignmentsAndTaskStatusDto> result = assignmentAttemptService.getTasksAndTaskStatus(pupil);

        assertThat(result.get(0).taskStatus()).isEqualTo(TaskStatus.FINISHED);
    }

    @Test
    void getTasksAndTaskStatus_unfinishedAttemptBeforeDue_isInProgress() {
        User pupil = User.builder().id(1L).username("pupil").build();
        Assignment assignment = assignmentWithDueDate(LocalDateTime.now().plusDays(1));
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(9L).assignment(assignment).pupil(pupil)
                .finishedAt(null).build();

        when(assignmentRepository.findAllByRecipientsPupil(pupil)).thenReturn(List.of(assignment));
        when(assignmentAttemptRepository.findAllByPupil(pupil)).thenReturn(List.of(attempt));

        List<ResponseAssignmentsAndTaskStatusDto> result = assignmentAttemptService.getTasksAndTaskStatus(pupil);

        assertThat(result.get(0).taskStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void getTasksAndTaskStatus_unfinishedAttemptPastDue_isOverdue() {
        User pupil = User.builder().id(1L).username("pupil").build();
        Assignment assignment = assignmentWithDueDate(LocalDateTime.now().minusHours(1));
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(9L).assignment(assignment).pupil(pupil)
                .finishedAt(null).build();

        when(assignmentRepository.findAllByRecipientsPupil(pupil)).thenReturn(List.of(assignment));
        when(assignmentAttemptRepository.findAllByPupil(pupil)).thenReturn(List.of(attempt));

        List<ResponseAssignmentsAndTaskStatusDto> result = assignmentAttemptService.getTasksAndTaskStatus(pupil);

        assertThat(result.get(0).taskStatus()).isEqualTo(TaskStatus.OVERDUE);
    }

    // ===== startAttempt =====

    @Test
    void startAttempt_newAttempt_shufflesAndSavesQuestionOrder() {
        User pupil = User.builder().id(1L).username("pupil").build();
        User teacher = User.builder().id(2L).username("teacher").build();
        QuestionSet set = QuestionSet.builder().id(5L).teacher(teacher).name("Set").build();
        Assignment assignment = Assignment.builder().id(1L).questionSet(set).assignedBy(teacher).build();

        Question q1 = Question.builder().id(1L).questionText("Q1").build();
        Question q2 = Question.builder().id(2L).questionText("Q2").build();

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(assignmentAttemptRepository.findByAssignmentIdAndPupilId(1L, 1L)).thenReturn(Optional.empty());
        when(assignmentAttemptRepository.save(any())).thenAnswer(inv -> {
            AssignmentAttempt a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });
        when(questionSetItemRepository.fetchQuestionsForSet(5L))
                .thenReturn(new java.util.ArrayList<>(List.of(q1, q2)));

        AttemptDto result = assignmentAttemptService.startAttempt(1L, pupil);

        assertThat(result.attemptId()).isEqualTo(100L);
        assertThat(result.totalQuestions()).isEqualTo(2);
        verify(attemptQuestionOrderRepository).saveAll(org.mockito.ArgumentMatchers.argThat(list -> {
            int count = 0;
            for (Object ignored : list) count++;
            return count == 2;
        }));
    }

    @Test
    void startAttempt_existingAttemptWithQuestionsAlready_doesNotReshuffleAgain() {
        User pupil = User.builder().id(1L).username("pupil").build();
        User teacher = User.builder().id(2L).username("teacher").build();
        QuestionSet set = QuestionSet.builder().id(5L).teacher(teacher).name("Set").build();
        Assignment assignment = Assignment.builder().id(1L).questionSet(set).assignedBy(teacher).build();
        AssignmentAttempt existing = AssignmentAttempt.builder().id(100L).assignment(assignment).pupil(pupil)
                .totalQuestions(3).build();

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(assignmentAttemptRepository.findByAssignmentIdAndPupilId(1L, 1L)).thenReturn(Optional.of(existing));

        AttemptDto result = assignmentAttemptService.startAttempt(1L, pupil);

        assertThat(result.totalQuestions()).isEqualTo(3);
        verify(questionSetItemRepository, never()).fetchQuestionsForSet(any());
        verify(attemptQuestionOrderRepository, never()).saveAll(any());
    }

    @Test
    void startAttempt_assignmentNotFound_throws() {
        User pupil = User.builder().id(1L).username("pupil").build();
        when(assignmentRepository.findById(1L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assignmentAttemptService.startAttempt(1L, pupil))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Assignment not found");
    }

    // ===== syncAttempt =====

    @Test
    void syncAttempt_recordsSelectedAnswerAndCorrectness() {
        User pupil = User.builder().id(1L).username("pupil").build();
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(100L).pupil(pupil)
                .startedAt(LocalDateTime.now().minusMinutes(1)).build();
        Question question = Question.builder().id(1L).questionText("Q1").build();
        Answer correctAnswer = Answer.builder().id(10L).answerText("A").isTrue(true).build();

        when(assignmentAttemptRepository.findByIdAndPupil(100L, pupil)).thenReturn(Optional.of(attempt));
        when(attemptAnswerRepository.findByAssignmentAttempt(attempt)).thenReturn(List.of());
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(answerRepository.findById(10L)).thenReturn(Optional.of(correctAnswer));

        SyncAttemptRequestDto request = new SyncAttemptRequestDto(100L, List.of(new AnswerSyncDto(1L, 10L)));
        assignmentAttemptService.syncAttempt(pupil, request);

        verify(attemptAnswerRepository).save(org.mockito.ArgumentMatchers.argThat(AttemptAnswer::isCorrect));
    }

    @Test
    void syncAttempt_alreadyFinished_doesNothing() {
        User pupil = User.builder().id(1L).username("pupil").build();
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(100L).pupil(pupil)
                .startedAt(LocalDateTime.now().minusMinutes(5)).finishedAt(LocalDateTime.now()).build();

        when(assignmentAttemptRepository.findByIdAndPupil(100L, pupil)).thenReturn(Optional.of(attempt));

        SyncAttemptRequestDto request = new SyncAttemptRequestDto(100L, List.of(new AnswerSyncDto(1L, 10L)));
        assignmentAttemptService.syncAttempt(pupil, request);

        verify(attemptAnswerRepository, never()).save(any());
        verify(questionRepository, never()).findById(any());
    }

    // ===== finishTaskSession =====

    @Test
    void finishTaskSession_computesPercentAndMarksFinished() {
        User pupil = User.builder().id(1L).username("pupil").build();
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(100L).pupil(pupil)
                .startedAt(LocalDateTime.now().minusMinutes(5)).totalQuestions(4).build();

        AttemptAnswer correct1 = AttemptAnswer.builder().id(1L).correct(true).build();
        AttemptAnswer correct2 = AttemptAnswer.builder().id(2L).correct(true).build();
        AttemptAnswer wrong = AttemptAnswer.builder().id(3L).correct(false).build();

        when(assignmentAttemptRepository.findByIdAndPupil(100L, pupil)).thenReturn(Optional.of(attempt));
        when(attemptAnswerRepository.findByAssignmentAttempt(attempt)).thenReturn(List.of(correct1, correct2, wrong));

        assignmentAttemptService.finishTaskSession(pupil, 100L);

        assertThat(attempt.getCorrectAnswers()).isEqualTo(2);
        assertThat(attempt.getPercent()).isEqualTo(50); // 2/4 = 50%
        assertThat(attempt.getFinishedAt()).isNotNull();
        verify(attemptHeartbeatService).heartbeat(pupil, 100L);
    }

    @Test
    void finishTaskSession_alreadyFinished_isIdempotent() {
        User pupil = User.builder().id(1L).username("pupil").build();
        LocalDateTime finishedAt = LocalDateTime.now().minusMinutes(1);
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(100L).pupil(pupil)
                .finishedAt(finishedAt).percent(80).build();

        when(assignmentAttemptRepository.findByIdAndPupil(100L, pupil)).thenReturn(Optional.of(attempt));

        assignmentAttemptService.finishTaskSession(pupil, 100L);

        assertThat(attempt.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(attempt.getPercent()).isEqualTo(80);
        verify(attemptHeartbeatService, never()).heartbeat(any(), any());
    }

    // ===== getFullAttemptByTaskId =====

    @Test
    void getFullAttemptByTaskId_noAttemptYet_returnsEmptyDto() {
        User pupil = User.builder().id(1L).username("pupil").build();
        when(assignmentAttemptRepository.findByAssignmentIdAndPupilId(1L, 1L)).thenReturn(Optional.empty());

        AttemptDto result = assignmentAttemptService.getFullAttemptByTaskId(1L, pupil);

        assertThat(result.attemptId()).isNull();
    }

    @Test
    void getFullAttemptByTaskId_existingAttempt_returnsPopulatedDto() {
        User pupil = User.builder().id(1L).username("pupil").build();
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(100L).pupil(pupil)
                .totalQuestions(5).correctAnswers(3).percent(60).build();
        when(assignmentAttemptRepository.findByAssignmentIdAndPupilId(1L, 1L)).thenReturn(Optional.of(attempt));

        AttemptDto result = assignmentAttemptService.getFullAttemptByTaskId(1L, pupil);

        assertThat(result.attemptId()).isEqualTo(100L);
        assertThat(result.percent()).isEqualTo(60);
    }
}
