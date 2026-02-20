package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.dto.student.*;
import behzoddev.testproject.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentAttemptService {

    public static final String ATTEMPT_NOT_FOUND = "Attempt not found";
    private final AssignmentAttemptRepository assignmentAttemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final QuestionSetItemRepository questionSetItemRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AttemptHeartbeatService attemptHeartbeatService;

    @Transactional
    public AttemptDto startAttempt(
            Long assignmentId,
            User pupil
    ) {

        Assignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() ->
                        new RuntimeException("Assignment not found"));

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findByAssignmentIdAndPupilId(
                                assignmentId,
                                pupil.getId()
                        )
                        .orElseGet(() -> createAttempt(assignment, pupil));

        // === load questions once ===
        List<Question> questions =
                questionSetItemRepository.fetchQuestionsForSet(
                        assignment.getQuestionSet().getId()
                );

        attempt.setTotalQuestions(questions.size());

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

        return AssignmentAttempt.builder()
                .assignment(assignment)
                .pupil(pupil)
                .totalQuestions(0)
                .correctAnswers(0)
                .percent(0)
                .durationSec(0)
                .startedAt(LocalDateTime.now())
                .lastSync(LocalDateTime.now())
                .build();
    }

    @Transactional
    public void syncAttempt(User pupil, SyncAttemptRequestDto request) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findByIdAndPupil(request.attemptId(), pupil)
                        .orElseThrow(() ->
                                new RuntimeException(ATTEMPT_NOT_FOUND));

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
        attempt.setCorrectAnswers(correct);
        attempt.setPercent(percent);
        attempt.setFinishedAt(now);
        attempt.setLastSync(now);

        // dirty checking сохранит всё автоматически
    }

    @Transactional(readOnly = true)
    public AttemptDto getFullAttemptByTaskId(Long taskId, User pupil) {
        Optional<AssignmentAttempt> attempt =
                assignmentAttemptRepository.findByAssignmentIdAndPupilId(taskId, pupil.getId());

        if (attempt.isPresent()) {
            AssignmentAttempt assignmentAttempt = attempt.get();

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

    @Transactional(readOnly = true)
    public AttemptFullDto getFullAttemptForResult(Long taskId, User pupil) {

        // 1️⃣ Загружаем попытку вместе с ответами
        AssignmentAttempt attempt =
                assignmentAttemptRepository
                        .findFullByTaskIdAndPupil(taskId, pupil)
                        .orElseThrow(() ->
                                new RuntimeException(ATTEMPT_NOT_FOUND));

        // 2️⃣ Получаем QuestionSet
        QuestionSet questionSet =
                attempt.getAssignment().getQuestionSet();

        // ⚠ Важно: вопросы должны быть загружены fetch join
        List<Question> questions =
                questionSet.getQuestions().stream().toList();

        // 3️⃣ Мапим вопросы
        List<ResponseQuestionDto> questionDtos =
                questions.stream()
                        .map(q -> new ResponseQuestionDto(
                                q.getId(),
                                q.getQuestionText(),
                                q.getAnswers().stream()
                                        .map(a -> new ResponseAnswerDto(
                                                a.getId(),
                                                a.getAnswerText(),
                                                a.getIsTrue()
                                        ))
                                        .toList()
                        ))
                        .toList();

        // 4️⃣ Мапим выбранные ответы ученика
        List<AttemptQuestionDto> attempted =
                attempt.getAnswers().stream()
                        .map(a -> new AttemptQuestionDto(
                                a.getQuestion().getId(),
                                a.getSelectedAnswer() != null
                                        ? a.getSelectedAnswer().getId()
                                        : null
                        ))
                        .toList();

        // 5️⃣ Возвращаем DTO
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

}



