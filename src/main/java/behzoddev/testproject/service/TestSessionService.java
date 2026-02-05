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


@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final TestSessionQuestionRepository testSessionQuestionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionMapper questionMapper;
    private final TestSessionRepository testSessionRepository;

    @Transactional
    public StartTestResponseDto startTest(User user, List<Long> topicIds, int limit) {

        // 1️⃣ создаём сессию
        TestSession session = new TestSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());
        testSessionRepository.save(session);

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

    // ✅ ЗАВЕРШЕНИЕ ТЕСТА
    @Transactional
    public void finishTest(FinishTestRequestDto request, User user) {

        TestSession session = testSessionRepository.findById(request.testSessionId())
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

            boolean isCorrect = Boolean.TRUE.equals(selected.getIsTrue());
            if (isCorrect) correct++;

            TestSessionQuestion testSessionQuestion =
                    TestSessionQuestion.builder()
                            .testSession(session)
                            .question(q)
                            .selectedAnswer(selected)
                            .isCorrect(isCorrect)
                            .build();

            testSessionQuestionRepository.save(testSessionQuestion);
            session.addQuestion(testSessionQuestion);
        }

        session.setTotalQuestions(total);
        session.setCorrectAnswers(correct);
        session.setWrongAnswers(total - correct);
        session.setPercent(total > 0 ? (correct * 100 / total) : 0);


        testSessionRepository.save(session);
    }


    // ✅ ИСТОРИЯ ТЕСТОВ
    @Transactional(readOnly = true)
    public Page<TestSessionHistoryDto> getHistory(User user, Pageable pageable) {

        return testSessionRepository.findByUserId(user.getId(), pageable);

    }

    // ✅ ДЕТАЛИ ТЕСТА
    @Transactional(readOnly = true)
    public List<TestSessionDetailDto> getDetails(Long testSessionid, User user) {

        TestSession session = testSessionRepository
                .findByIdAndUserId(testSessionid, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Нет доступа"));

        return testSessionQuestionRepository.findByTestSessionId(session.getId())
                .stream()
                .map(q -> new TestSessionDetailDto(
                        q.getQuestion().getQuestionText(),
                        q.getSelectedAnswer().getAnswerText(),
                        q.getQuestion().getAnswers().stream()
                                .filter(Answer::getIsTrue)
                                .findFirst()
                                .map(Answer::getAnswerText)
                                .orElse(""),
                        q.getQuestion().getAnswers().stream()
                                .filter(answer -> answer.getIsTrue() == true)
                                .findFirst()
                                .map(Answer::getCommentary)
                                .orElse(""),
                        q.getIsCorrect()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TestStatsDto getStats(User user) {
        List<TestSession> sessions =
                testSessionRepository.findByUserId(user.getId()).stream()
                        .filter(testSession -> testSession.getFinishedAt() != null) //Faqat yakuniga yetkazilgan testlar
                        .toList();

        int totalTests = sessions.size();
        int avgPercent = sessions.stream().mapToInt(TestSession::getPercent).sum();
        avgPercent = totalTests > 0 ? avgPercent / totalTests : 0;

        int best = sessions.stream().mapToInt(TestSession::getPercent).max().orElse(0);
        int worst = sessions.stream().mapToInt(TestSession::getPercent).min().orElse(0);
        long totalDuration = sessions.stream().mapToLong(TestSession::getDurationSec).sum();

        return new TestStatsDto(totalTests, avgPercent, best, worst, totalDuration);
    }
}
