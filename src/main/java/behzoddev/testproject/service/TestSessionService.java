package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TestSessionQuestionRepository;
import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.*;
import behzoddev.testproject.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final TestSessionRepository testSessionRepo;
    private final TestSessionQuestionRepository tsqRepo;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionMapper questionMapper;

    @Transactional
    public StartTestResponseDto startTest(User user, List<Long> topicIds, int limit) {

        // 1️⃣ создаём сессию
        TestSession session = new TestSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());
        testSessionRepo.save(session);

        // 2️⃣ получаем вопросы
        List<Question> questions = questionRepository.findRandomQuestionsByTopicIds(topicIds);

        Collections.shuffle(questions);
        questions = questions.stream().limit(limit).toList();

        List<QuestionDto> questionDtoListWithShuffledAnswers = questionMapper.mapQuestionListToQuestionDtoList(questions).stream()
                .map(dto -> {
                    List<AnswerDto> answerDtoList = dto.answers();

                    Collections.shuffle(answerDtoList);

                    return new QuestionDto(dto.id(), dto.questionText(), answerDtoList);
                }).toList();

        // 3️⃣ возвращаем ID + вопросы
        return new StartTestResponseDto(
                session.getId(),
                questionDtoListWithShuffledAnswers
        );
    }

    @Transactional
    public int checkAnswers(Map<Long, Long> answers) {
        int correct = 0;

        for (var entry : answers.entrySet()) {
            if (answerRepository.isCorrect(entry.getKey(), entry.getValue())) {
                correct++;
            }
        }
        return correct;
    }

    // ✅ ЗАВЕРШЕНИЕ ТЕСТА
    @Transactional
    public void finishTest(FinishTestRequestDto request, User user) {

        TestSession session = testSessionRepo.findById(request.testSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Test session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your test session");
        }

        int total = request.answers().size();
        int correct = 0;

        session.setStartedAt(
                Instant.ofEpochMilli(request.startedAt())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
        );

        session.setFinishedAt(
                Instant.ofEpochMilli(request.finishedAt())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
        );

        session.setDurationSec(
                (request.finishedAt() - request.startedAt()) / 1000
        );

        for (AnswerResultDto dto : request.answers()) {

            Question q = questionRepository.findById(dto.questionId())
                    .orElseThrow();

            Answer selected = answerRepository.findById(dto.answerId())
                    .orElseThrow();

            Boolean isCorrect = Boolean.TRUE.equals(selected.getIsTrue());
            if (isCorrect) correct++;

            TestSessionQuestion testSessionQuestion =
                    TestSessionQuestion.builder()
                            .testSession(session)
                            .question(q)
                            .selectedAnswer(selected)
                            .isCorrect(isCorrect)
                            .build();

            tsqRepo.save(testSessionQuestion);
            session.addQuestion(testSessionQuestion);
        }

        session.setTotalQuestions(total);
        session.setCorrectAnswers(correct);
        session.setWrongAnswers(total - correct);
        session.setPercent(total > 0 ? (correct * 100 / total) : 0);



        testSessionRepo.save(session);
    }


    // ✅ ИСТОРИЯ ТЕСТОВ
    @Transactional(readOnly = true)
    public Page<TestSessionHistoryDto> getHistory(User user, Pageable pageable) {

        return testSessionRepo.findByUserId(user.getId(), pageable)
                .map(s -> new TestSessionHistoryDto(
                        s.getId(),
                        s.getTotalQuestions(),
                        s.getCorrectAnswers(),
                        s.getPercent(),
                        s.getFinishedAt(),
                        s.getDurationSec()
                ));
    }

    // ✅ ДЕТАЛИ ТЕСТА
    @Transactional(readOnly = true)
    public List<TestSessionDetailDto> getDetails(Long testSessionid, User user) {

        TestSession session = testSessionRepo
                .findByIdAndUserId(testSessionid, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Нет доступа"));

        return tsqRepo.findByTestSessionId(session.getId())
                .stream()
                .map(q -> new TestSessionDetailDto(
                        q.getQuestion().getQuestionText(),
                        q.getSelectedAnswer().getAnswerText(),
                        q.getQuestion().getAnswers().stream()
                                .filter(Answer::getIsTrue)
                                .findFirst()
                                .map(Answer::getAnswerText)
                                .orElse(""),
                        q.getIsCorrect()
                ))
                .toList();
    }
}
