package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.dto.student.AnswerSyncDto;
import behzoddev.testproject.dto.student.AttemptStartResponseDto;
import behzoddev.testproject.dto.student.SyncAttemptRequestDto;
import behzoddev.testproject.entity.*;
import behzoddev.testproject.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentAttemptService {

    private final AssignmentAttemptRepository assignmentAttemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final QuestionMapper questionMapper;
    private final QuestionSetItemRepository questionSetItemRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    @Transactional
    public AttemptStartResponseDto startAttempt(
            Long assignmentId,
            User pupil
    ) {

        Assignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() ->
                        new RuntimeException("Assignment not found"));

        // 🔥 ищем существующую попытку
        AssignmentAttempt attempt = assignmentAttemptRepository
                .findByAssignmentIdAndPupilId(
                        assignmentId,
                        pupil.getId()
                )
                .orElseGet(() -> createAttempt(assignment, pupil));

        // 🔥 если уже начата — просто восстановление
        if (attempt.getStartedAt() == null) {
            attempt.setStartedAt(LocalDateTime.now());
        }

        // загружаем вопросы пакета
        List<Question> questions =
                questionSetItemRepository.fetchQuestionsForSet(
                        assignment.getQuestionSet().getId()
                );

        // pre-create answers
        if (attempt.getAnswers().isEmpty()) {

            for (Question q : questions) {

                AttemptAnswer aa =
                        AttemptAnswer.builder()
                                .assignmentAttempt(attempt)
                                .question(q)
                                .correct(false)
                                .build();

                attempt.addAnswer(aa);
            }
        }

        attempt.setTotalQuestions(questions.size());

        assignmentAttemptRepository.save(attempt);

        return new AttemptStartResponseDto(

                attempt.getId(),
                attempt.getStartedAt(),
                questionMapper.mapQuestionListToResponseQuestionDtoList(questions)
        );
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
                .build();
    }

    public void syncAttempt(User pupil, SyncAttemptRequestDto request) {

        AssignmentAttempt attempt = assignmentAttemptRepository
                .findByIdAndPupil(request.attemptId(), pupil)
                .orElseThrow(() ->
                        new RuntimeException("Attempt not found"));

        Map<Long, AttemptAnswer> existing =
                attemptAnswerRepository
                        .findByAssignmentAttempt(attempt)
                        .stream()
                        .collect(Collectors.toMap(
                                a -> a.getQuestion().getId(),
                                a -> a
                        ));

        for (AnswerSyncDto dto : request.answers()) {

            if (dto.selectedAnswerId() == null)
                continue;

            Question question =
                    questionRepository.findById(dto.questionId())
                            .orElseThrow();

            Answer answer = null;

            if (dto.selectedAnswerId() != null) {
                answer = answerRepository
                        .findById(dto.selectedAnswerId())
                        .orElseThrow(() ->
                                new RuntimeException("Javob topilmadi."));
            }

            AttemptAnswer record =
                    existing.get(question.getId());

            if (record == null) {

                record = new AttemptAnswer();
                record.setAssignmentAttempt(attempt);
                record.setQuestion(question);
            }

            record.setSelectedAnswer(answer);

            if (answer.getIsTrue()){
                record.setCorrect(true);
            }

            attemptAnswerRepository.save(record);
        }

        attempt.setLastSync(LocalDateTime.now());
    }
}



