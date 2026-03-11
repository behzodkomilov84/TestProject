package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.dto.student.*;
import behzoddev.testproject.entity.*;
import behzoddev.testproject.entity.enums.TaskStatus;
import behzoddev.testproject.telegram.dao.AttemptQuestionOrderRepository;
import behzoddev.testproject.telegram.entity.AttemptQuestionOrder;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentAttemptService {

    public static final String ATTEMPT_NOT_FOUND = "Attempt not found";
    public static final String ASSIGNMENT_NOT_FOUND = "Assignment not found";
    private final AssignmentAttemptRepository assignmentAttemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final QuestionSetItemRepository questionSetItemRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AttemptHeartbeatService attemptHeartbeatService;
    private final AttemptQuestionOrderRepository attemptQuestionOrderRepository;

    public static List<ResponseQuestionDto> getResponseQuestionDtos(QuestionSet questionSet) {
        List<Question> questions =
                new ArrayList<>(questionSet.getQuestions());

        Collections.shuffle(questions);

        return getResponseQuestionDtos(questions);
    }

    public static List<ResponseQuestionDto> getResponseQuestionDtosForTelegramBot(
            QuestionSet questionSet) {

        return questionSet.getQuestions()
                .stream()
                .map(q -> new ResponseQuestionDto(
                        q.getId(),
                        q.getQuestionText(),
                        q.getAnswers()
                                .stream()
                                .map(a -> new ResponseAnswerDto(
                                        a.getId(),
                                        a.getAnswerText(),
                                        a.getIsTrue()
                                ))
                                .toList()
                ))
                .toList();
    }

    @NotNull
    private static List<ResponseQuestionDto> getResponseQuestionDtos(List<Question> questions) {
        return questions.stream()
                .map(q -> {

                    // mutable copy ответов
                    List<Answer> answers =
                            new ArrayList<>(q.getAnswers());

                    Collections.shuffle(answers);

                    return new ResponseQuestionDto(
                            q.getId(),
                            q.getQuestionText(),
                            answers.stream()
                                    .map(a -> new ResponseAnswerDto(
                                            a.getId(),
                                            a.getAnswerText(),
                                            a.getIsTrue()
                                    ))
                                    .toList()
                    );
                })
                .toList();
    }

    private static @NotNull TaskStatus getTaskStatus(Assignment a, AssignmentAttempt attempt, LocalDateTime now) {
        TaskStatus status;

        if (attempt == null) {
            status = a.getDueDate().isBefore(now)
                    ? TaskStatus.OVERDUE
                    : TaskStatus.NEW;

        } else if (attempt.getFinishedAt() != null) {
            status = TaskStatus.FINISHED;

        } else if (a.getDueDate().isBefore(now)) {
            status = TaskStatus.OVERDUE;

        } else {
            status = TaskStatus.IN_PROGRESS;
        }
        return status;
    }

    public static void updateDuration(AssignmentAttempt attempt) {

        if (attempt.getStartedAt() == null) return;
        if (attempt.getFinishedAt() != null) return;

        LocalDateTime now = LocalDateTime.now();

        if (attempt.getLastSync() != null) {

            long seconds = java.time.Duration
                    .between(attempt.getLastSync(), now)
                    .getSeconds();

            if (seconds > 300) {
                seconds = 300; // максимум 5 минут за один раз
            }

            if (seconds > 0) {
                attempt.setDurationSec(
                        attempt.getDurationSec() + (int) seconds
                );
            }
        }

        attempt.setLastSync(now);
    }

    @Transactional
    public AttemptDto startAttempt(Long assignmentId, @NotNull User pupil) {

        Assignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException(ASSIGNMENT_NOT_FOUND));

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findByAssignmentIdAndPupilId(
                                assignmentId,
                                pupil.getId()
                        )
                        .orElseGet(() -> createAttempt(assignment, pupil));

        if (attempt.getTotalQuestions() == 0) {

            List<Question> questions =
                    questionSetItemRepository.fetchQuestionsForSet(
                            assignment.getQuestionSet().getId()
                    );

            Collections.shuffle(questions);

            int position = 0;

            List<AttemptQuestionOrder> orders = new ArrayList<>();

            for (Question q : questions) {

                orders.add(AttemptQuestionOrder.builder()
                                .attempt(attempt)
                                .question(q)
                                .position(position++)
                                .build());
            }
                attemptQuestionOrderRepository.saveAll(orders);

            attempt.setTotalQuestions(questions.size());
        }

        assignmentAttemptRepository.save(attempt);

        return AttemptDto.builder()
                .attemptId(attempt.getId())
                .totalQuestions(attempt.getTotalQuestions())
                .correctAnswers(attempt.getCorrectAnswers())
                .percent(attempt.getPercent())
                .durationSec(attempt.getDurationSec())
                .startedAt(attempt.getStartedAt())
                .finishedAt(attempt.getFinishedAt())
                .lastSync(attempt.getLastSync())
                .build();
    }

    private AssignmentAttempt createAttempt(
            Assignment assignment,
            User pupil
    ) {

        return assignmentAttemptRepository.save(AssignmentAttempt.builder()
                .assignment(assignment)
                .pupil(pupil)
                .totalQuestions(0)
                .correctAnswers(0)
                .percent(0)
                .durationSec(0)
                .startedAt(LocalDateTime.now())
                .lastSync(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void syncAttempt(User pupil, SyncAttemptRequestDto request) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findByIdAndPupil(request.attemptId(), pupil)
                        .orElseThrow(() ->
                                new RuntimeException(ATTEMPT_NOT_FOUND));

        updateDuration(attempt);

        if (attempt.getFinishedAt() != null) {
            return;
        }

        // все существующие ответы попытки
        Map<Long, AttemptAnswer> existing =
                attemptAnswerRepository
                        .findByAssignmentAttempt(attempt)
                        .stream()
                        .collect(Collectors.toMap(
                                a -> a.getQuestion().getId(),
                                a -> a
                        ));

        for (AnswerSyncDto dto : request.answers()) {

            Question question =
                    questionRepository.findById(dto.questionId())
                            .orElseThrow(() ->
                                    new RuntimeException("Savol topilmadi"));

            AttemptAnswer attemptAnswer =
                    existing.getOrDefault(question.getId(), null);

            if (attemptAnswer == null) {

                attemptAnswer = new AttemptAnswer();
                attemptAnswer.setAssignmentAttempt(attempt);
                attemptAnswer.setQuestion(question);
            }

            // ===== обработка выбранного ответа =====

            Answer selected = null;
            boolean correct = false;

            if (dto.selectedAnswerId() != null) {

                selected = answerRepository
                        .findById(dto.selectedAnswerId())
                        .orElseThrow(() ->
                                new RuntimeException("Answer not found"));

                correct = Boolean.TRUE.equals(selected.getIsTrue());
            }

            // обновляем ВСЕ поля
            attemptAnswer.setSelectedAnswer(selected);
            attemptAnswer.setCorrect(correct);

            attemptAnswerRepository.save(attemptAnswer);
        }

        attempt.setLastSync(LocalDateTime.now());
    }

    @Transactional
    public void finishTaskSession(User pupil, Long attemptId) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findByIdAndPupil(attemptId, pupil)
                        .orElseThrow(() ->
                                new RuntimeException(ATTEMPT_NOT_FOUND));

        // уже завершена — просто выходим
        if (attempt.getFinishedAt() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // === гарантируем startedAt ===
        if (attempt.getStartedAt() == null) {
            attempt.setStartedAt(now);
        }

        // финальный heartbeat
        attemptHeartbeatService.heartbeat(pupil, attemptId);


        // === загрузка всех ответов попытки ===
        List<AttemptAnswer> answers =
                attemptAnswerRepository
                        .findByAssignmentAttempt(attempt);

        int total = attempt.getTotalQuestions();
        int correct = 0;

        for (AttemptAnswer a : answers) {

            if (Boolean.TRUE.equals(a.isCorrect())) {
                correct++;
            }
        }

        // === процент ===
        int percent = total == 0
                ? 0
                : (int) Math.round((correct * 100.0) / total);

        // === обновляем attempt ===
        updateDuration(attempt);

        attempt.setCorrectAnswers(correct);
        attempt.setPercent(percent);
        attempt.setFinishedAt(now);
        attempt.setLastSync(now);

        // dirty checking сохранит всё автоматически
    }

    @Transactional
    public AttemptDto getFullAttemptByTaskId(Long taskId, User pupil) {
        Optional<AssignmentAttempt> attempt =
                assignmentAttemptRepository.findByAssignmentIdAndPupilId(taskId, pupil.getId());

        if (attempt.isPresent()) {
            AssignmentAttempt assignmentAttempt = attempt.get();

            updateDuration(assignmentAttempt);

            return AttemptDto.builder()
                    .attemptId(assignmentAttempt.getId())
                    .totalQuestions(assignmentAttempt.getTotalQuestions())
                    .correctAnswers(assignmentAttempt.getCorrectAnswers())
                    .percent(assignmentAttempt.getPercent())
                    .durationSec(assignmentAttempt.getDurationSec())
                    .startedAt(assignmentAttempt.getStartedAt())
                    .finishedAt(assignmentAttempt.getFinishedAt())
                    .lastSync(assignmentAttempt.getLastSync())
                    .build();
        } else {
            return AttemptDto.builder().build();
        }
    }

    @Transactional
    public AttemptFullDto getFullAttemptForResult(Long taskId, User pupil) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findFullByTaskIdAndPupil(taskId, pupil)
                        .orElseThrow(() ->
                                new RuntimeException(ATTEMPT_NOT_FOUND));

        return getAttemptFullDto(attempt);
    }

    @Transactional(readOnly = true)
    public List<ResponseAssignmentsAndTaskStatusDto> getTasksAndTaskStatus(User pupil) {

        List<Assignment> assignments =
                assignmentRepository.findAllByRecipientsPupil(pupil);

        List<AssignmentAttempt> attempts =
                assignmentAttemptRepository.findAllByPupil(pupil);

        Map<Long, AssignmentAttempt> attemptMap =
                attempts.stream()
                        .collect(Collectors.toMap(
                                a -> a.getAssignment().getId(),
                                Function.identity()
                        ));

        LocalDateTime now = LocalDateTime.now();

        return assignments.stream()
                .map(a -> {

                    AssignmentAttempt attempt =
                            attemptMap.get(a.getId());

                    TaskStatus status = getTaskStatus(a, attempt, now);

                    return ResponseAssignmentsAndTaskStatusDto.builder()
                            .id(a.getId())
                            .questionSetId(a.getQuestionSet().getId())
                            .questionSetName(a.getQuestionSet().getName())
                            .groupId(a.getGroup().getId())
                            .groupName(a.getGroup().getName())
                            .assignerId(a.getAssignedBy().getId())
                            .assignerName(a.getAssignedBy().getUsername())
                            .assignedAt(a.getAssignedAt())
                            .dueDate(a.getDueDate())
                            .taskStatus(status)
                            .build();
                })
                .toList();
    }

    @Transactional
    public AssignmentAttempt updateAndGetTime(User pupil, Long id) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findByIdAndPupil(id, pupil)
                        .orElseThrow(() -> new RuntimeException(ATTEMPT_NOT_FOUND));

        updateDuration(attempt);

        return attempt;
    }

    @Transactional(readOnly = true)
    public AttemptFullDto getFullAttemptForTestSessionOfBot(Long attemptId) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findById(attemptId)
                        .orElseThrow(() -> new RuntimeException(ATTEMPT_NOT_FOUND));

        return getAttemptFullDtoForTelegramBot(attempt);
    }


    @NotNull
    private AttemptFullDto getAttemptFullDto(AssignmentAttempt attempt) {
        updateDuration(attempt);

        QuestionSet questionSet =
                attempt.getAssignment().getQuestionSet();

        List<ResponseQuestionDto> questionDtos =
                getResponseQuestionDtos(questionSet);

        return getAttemptFullDto(attempt, questionDtos);
    }

    @NotNull
    private AttemptFullDto getAttemptFullDtoForTelegramBot(AssignmentAttempt attempt) {
        updateDuration(attempt);

        QuestionSet questionSet =
                attempt.getAssignment().getQuestionSet();

        List<ResponseQuestionDto> questionDtos =
                getResponseQuestionDtosForTelegramBot(questionSet);

        return getAttemptFullDto(attempt, questionDtos);
    }

    @NotNull
    private AttemptFullDto getAttemptFullDto(AssignmentAttempt attempt, List<ResponseQuestionDto> questionDtos) {
        List<AttemptQuestionDto> attempted =
                attempt.getAnswers().stream()
                        .map(a -> new AttemptQuestionDto(
                                a.getQuestion().getId(),
                                a.getSelectedAnswer() != null
                                        ? a.getSelectedAnswer().getId()
                                        : null
                        ))
                        .toList();

        return new AttemptFullDto(
                attempt.getId(),
                attempt.getTotalQuestions(),
                attempt.getCorrectAnswers(),
                attempt.getPercent(),
                attempt.getDurationSec(),
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                attempt.getLastSync(),
                questionDtos,
                attempted
        );
    }

    @Transactional(readOnly = true)
    public List<Question> getQuestionsForAttempt(Long attemptId) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findById(attemptId)
                        .orElseThrow();

        return attemptQuestionOrderRepository
                .findByAttemptOrderByPosition(attempt)
                .stream()
                .map(AttemptQuestionOrder::getQuestion)
                .toList();
    }
}




