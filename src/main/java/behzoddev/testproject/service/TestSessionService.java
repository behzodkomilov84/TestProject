package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.dto.answer.AnswerDto;
import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.testsession.*;
import behzoddev.testproject.entity.*;
import behzoddev.testproject.entity.compositeKey.UserQuestionKey;
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
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final TestSessionQuestionRepository testSessionQuestionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;
    private final TestSessionRepository testSessionRepository;
    private final UserQuestionStatsRepository userQuestionStatsRepository;


    @Transactional
    public StartTestResponseDto startTest(User user, List<Long> topicIds, int limit, String mode) {

        // 1️⃣ создаём сессию
        TestSession session = new TestSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());
        testSessionRepository.save(session);

        // 2️⃣ получаем вопросы
        List<Question> questions;

        if ("hard".equals(mode)) {
            questions = questionRepository.findHardForUser(user.getId(), topicIds);
        } else {
            questions = questionRepository.findRandomQuestionsByTopicIds(topicIds);
        }


        Collections.shuffle(questions);
        questions = questions.stream().limit(limit).toList();
        // 3️⃣ возвращаем вопросы с перемешанными ответами
        List<QuestionDto> questionDtoListWithShuffledAnswers = questionMapper
                .mapQuestionListToQuestionDtoList(questions).stream()
                .map(dto -> {
                    List<AnswerDto> answerDtoList = dto.answers();

                    Collections.shuffle(answerDtoList);

                    return new QuestionDto(dto.id(), dto.questionText(), dto.imageUrl(), answerDtoList);
                }).toList();

        // возвращаем ID + вопросы
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

        // Testda AJRATILGAN savollar soni bo'yicha hisoblanadi (javob
        // berilganlar soni emas) — aks holda vaqt tugab, ba'zi savollarga
        // ulgurmagan foydalanuvchi "1/1" (100%) kabi noto'g'ri natija
        // ko'rardi, "1/2" o'rniga. Eski klient (totalQuestions yubormasa)
        // uchun avvalgi xatti-harakat (javoblar soni) — himoya sifatida.
        int total = request.totalQuestions() != null ? request.totalQuestions() : request.answers().size();
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

            // Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi,
            // 2026-09-05) — eski "answerId" (bitta) YOKI yangi "answerIds"
            // (ro'yxat) dan qat'i nazar, MultiAnswerUtil bir xil formula
            // bilan hisoblaydi (AssignmentAttemptService'dagi bilan BIR
            // XIL — MultiAnswerUtil).
            List<Long> submittedIds = MultiAnswerUtil.resolveSubmittedIds(dto.answerId(), dto.answerIds());
            List<Answer> selectedAnswers = MultiAnswerUtil.resolveAnswers(submittedIds, q.getAnswers());
            if (selectedAnswers.isEmpty()) {
                throw new IllegalArgumentException("❌ Javob topilmadi");
            }

            boolean isCorrect = MultiAnswerUtil.isCorrect(submittedIds, MultiAnswerUtil.correctAnswerIds(q.getAnswers()));
            if (isCorrect) correct++;

            // ===== ПЕРСОНАЛЬНАЯ СЛОЖНОСТЬ =====
            UserQuestionKey key = new UserQuestionKey(user.getId(), q.getId());
            UserQuestionStats userQuestionStats = userQuestionStatsRepository.findById(key)
                    .orElse(UserQuestionStats.builder()
                            .id(key)
                            .totalAttempts(0)
                            .correctAttempts(0)
                            .build());

            userQuestionStats.setTotalAttempts(userQuestionStats.getTotalAttempts() + 1);
            if (isCorrect) userQuestionStats.setCorrectAttempts(userQuestionStats.getCorrectAttempts() + 1);
            userQuestionStatsRepository.save(userQuestionStats);

            // ===== СЕССИЯ =====
            TestSessionQuestion testSessionQuestion =
                    TestSessionQuestion.builder()
                            .testSession(session)
                            .question(q)
                            .selectedAnswer(selectedAnswers.get(0))
                            .selectedAnswerIds(MultiAnswerUtil.join(submittedIds))
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
    public PageResponseDto<TestSessionHistoryDto> getHistory(User user, Pageable pageable) {

        Page<TestSessionHistoryDto> pageData = testSessionRepository.findByUserId(user.getId(), pageable);

        List<TestSessionHistoryDto> dtos = pageData.getContent();

        return new PageResponseDto<>(
                dtos,
                pageData.getTotalPages(),
                pageData.getNumber(),
                pageData.isFirst(),
                pageData.isLast()
        );

    }

    // ✅ ДЕТАЛИ ТЕСТА
    @Transactional(readOnly = true)
    public List<TestSessionDetailDto> getDetails(Long testSessionid, User user) {

        TestSession session = testSessionRepository
                .findByIdAndUserId(testSessionid, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Нет доступа"));

        return testSessionQuestionRepository.findByTestSessionId(session.getId())
                .stream()
                .map(q -> {
                    List<Answer> questionAnswers = q.getQuestion().getAnswers();

                    // Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi,
                    // 2026-09-05) — selectedAnswerIds bo'lsa SHU ustundan
                    // (bir nechta bo'lishi mumkin), eski (import qilingan
                    // yoki hali yangilanmagan) yozuvlarda esa selectedAnswer
                    // (bitta) ustunidan o'qiladi.
                    List<Long> selectedIds = !MultiAnswerUtil.parse(q.getSelectedAnswerIds()).isEmpty()
                            ? MultiAnswerUtil.parse(q.getSelectedAnswerIds())
                            : (q.getSelectedAnswer() != null ? List.of(q.getSelectedAnswer().getId()) : List.of());
                    List<Answer> selectedAnswers = MultiAnswerUtil.resolveAnswers(selectedIds, questionAnswers);

                    // To'g'ri javob(lar) — ilgari faqat BIRINCHISI (findFirst())
                    // olinardi, bu ko'p to'g'ri javobli savolda qolganlarini
                    // "yashirib" qo'yardi. Endi HAMMASI vergul bilan qo'shiladi.
                    List<Answer> correctAnswers = questionAnswers.stream()
                            .filter(a -> Boolean.TRUE.equals(a.getIsTrue()))
                            .toList();

                    return new TestSessionDetailDto(
                            q.getQuestion().getQuestionText(),
                            selectedAnswers.stream().map(Answer::getAnswerText).collect(Collectors.joining(", ")),
                            correctAnswers.stream().map(Answer::getAnswerText).collect(Collectors.joining(", ")),
                            correctAnswers.stream()
                                    .map(Answer::getCommentary)
                                    .filter(c -> c != null && !c.isBlank())
                                    .collect(Collectors.joining(" ")),
                            q.getIsCorrect()
                    );
                })
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

    @Transactional
    public void cancelTest(Map<String, Long> payload, User user) {
        Long testSessionId = payload.get("testSessionId");

        TestSession session = testSessionRepository.findById(testSessionId)
                .orElse(null);

        if (session != null && session.getUser().getId().equals(user.getId())) {
            testSessionRepository.delete(session);
        }
    }
}
