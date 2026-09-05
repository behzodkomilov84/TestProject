package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TestSessionQuestionRepository;
import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dao.UserQuestionStatsRepository;
import behzoddev.testproject.dto.PageResponseDto;
import behzoddev.testproject.dto.answer.AnswerDto;
import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.testsession.AnswerResultDto;
import behzoddev.testproject.dto.testsession.FinishTestRequestDto;
import behzoddev.testproject.dto.testsession.TestSessionDetailDto;
import behzoddev.testproject.dto.testsession.TestSessionHistoryDto;
import behzoddev.testproject.dto.testsession.TestStatsDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.TestSession;
import behzoddev.testproject.entity.TestSessionQuestion;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.UserQuestionStats;
import behzoddev.testproject.entity.compositeKey.UserQuestionKey;
import behzoddev.testproject.mapper.QuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test topshirish jarayoni — ball hisoblash, shaxsiy qiyinlik statistikasi
 * (UserQuestionStats) va faqat egasiga ruxsat berish (AccessDenied) asosiy
 * e'tibor markazi.
 */
@ExtendWith(MockitoExtension.class)
class TestSessionServiceTest {

    @Mock
    private TestSessionQuestionRepository testSessionQuestionRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private TestSessionRepository testSessionRepository;
    @Mock
    private UserQuestionStatsRepository userQuestionStatsRepository;

    @InjectMocks
    private TestSessionService testSessionService;

    private User user;

    private User freshUser() {
        return User.builder().id(1L).username("student").build();
    }

    // ===== startTest =====

    @Test
    void startTest_randomMode_limitsToRequestedCount() {
        user = freshUser();
        Question q1 = Question.builder().id(1L).questionText("Q1").build();
        Question q2 = Question.builder().id(2L).questionText("Q2").build();
        Question q3 = Question.builder().id(3L).questionText("Q3").build();

        when(testSessionRepository.save(any())).thenAnswer(inv -> {
            TestSession s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(questionRepository.findRandomQuestionsByTopicIds(List.of(1L)))
                .thenReturn(new ArrayList<>(List.of(q1, q2, q3)));
        when(questionMapper.mapQuestionListToQuestionDtoList(anyList())).thenAnswer(inv -> {
            List<Question> qs = inv.getArgument(0);
            return qs.stream().map(q -> QuestionDto.builder().id(q.getId()).questionText(q.getQuestionText())
                    .answers(new ArrayList<>()).build()).toList();
        });

        var result = testSessionService.startTest(user, List.of(1L), 2, "random");

        assertThat(result.testSessionId()).isEqualTo(10L);
        assertThat(result.questions()).hasSize(2);
    }

    @Test
    void startTest_hardMode_usesHardQuestionsForUser() {
        user = freshUser();
        Question q1 = Question.builder().id(1L).questionText("Q1").build();

        when(testSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findHardForUser(1L, List.of(1L))).thenReturn(new ArrayList<>(List.of(q1)));
        when(questionMapper.mapQuestionListToQuestionDtoList(anyList()))
                .thenReturn(List.of(QuestionDto.builder().id(1L).questionText("Q1").answers(new ArrayList<>()).build()));

        var result = testSessionService.startTest(user, List.of(1L), 5, "hard");

        assertThat(result.questions()).hasSize(1);
        verify(questionRepository, never()).findRandomQuestionsByTopicIds(any());
    }

    // ===== finishTest =====

    @Test
    void finishTest_computesScoreAndCreatesNewStatsForFirstAttempt() {
        user = freshUser();
        TestSession session = TestSession.builder().id(10L).user(user).questions(new ArrayList<>()).build();
        Answer correctAnswer = Answer.builder().id(100L).answerText("A").isTrue(true).build();
        Question q1 = Question.builder().id(1L).questionText("Q1").answers(List.of(correctAnswer)).build();

        when(testSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q1));
        when(userQuestionStatsRepository.findById(any())).thenReturn(Optional.empty());

        FinishTestRequestDto request = new FinishTestRequestDto(10L,
                System.currentTimeMillis() - 60_000, System.currentTimeMillis(),
                List.of(new AnswerResultDto(1L, 100L)), 1);

        testSessionService.finishTest(request, user);

        assertThat(session.getTotalQuestions()).isEqualTo(1);
        assertThat(session.getCorrectAnswers()).isEqualTo(1);
        assertThat(session.getWrongAnswers()).isZero();
        assertThat(session.getPercent()).isEqualTo(100);

        var captor = org.mockito.ArgumentCaptor.forClass(UserQuestionStats.class);
        verify(userQuestionStatsRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getCorrectAttempts()).isEqualTo(1);
    }

    @Test
    void finishTest_wrongAnswer_updatesExistingStatsWithoutIncrementingCorrect() {
        user = freshUser();
        TestSession session = TestSession.builder().id(10L).user(user).questions(new ArrayList<>()).build();
        Answer wrongAnswer = Answer.builder().id(100L).answerText("B").isTrue(false).build();
        Question q1 = Question.builder().id(1L).questionText("Q1").answers(List.of(wrongAnswer)).build();

        UserQuestionStats existingStats = UserQuestionStats.builder()
                .id(new UserQuestionKey(1L, 1L)).totalAttempts(3).correctAttempts(2).build();

        when(testSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q1));
        when(userQuestionStatsRepository.findById(any())).thenReturn(Optional.of(existingStats));

        FinishTestRequestDto request = new FinishTestRequestDto(10L,
                System.currentTimeMillis() - 1000, System.currentTimeMillis(),
                List.of(new AnswerResultDto(1L, 100L)), 1);

        testSessionService.finishTest(request, user);

        assertThat(existingStats.getTotalAttempts()).isEqualTo(4);
        assertThat(existingStats.getCorrectAttempts()).isEqualTo(2); // o'zgarmadi
        assertThat(session.getPercent()).isZero();
    }

    @Test
    void finishTest_fewerAnswersThanTotalQuestions_computesScoreAgainstRealTotal() {
        // Haqiqiy production bug: Exam rejimida vaqt tugab, 2 savoldan
        // faqat 1 tasiga ulgurgan foydalanuvchi "1/1 (100%)" ko'rardi,
        // "1/2 (50%)" o'rniga — chunki eski kod totalQuestions'ni
        // answers.size()'dan (ya'ni JAVOB BERILGANLAR sonidan) hisoblardi.
        user = freshUser();
        TestSession session = TestSession.builder().id(10L).user(user).questions(new ArrayList<>()).build();
        Answer correctAnswer = Answer.builder().id(100L).answerText("A").isTrue(true).build();
        Question q1 = Question.builder().id(1L).questionText("Q1").answers(List.of(correctAnswer)).build();

        when(testSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q1));
        when(userQuestionStatsRepository.findById(any())).thenReturn(Optional.empty());

        // 2 ta savol AJRATILGAN edi (totalQuestions=2), lekin faqat 1 tasiga javob berildi.
        FinishTestRequestDto request = new FinishTestRequestDto(10L,
                System.currentTimeMillis() - 60_000, System.currentTimeMillis(),
                List.of(new AnswerResultDto(1L, 100L)), 2);

        testSessionService.finishTest(request, user);

        assertThat(session.getTotalQuestions()).isEqualTo(2);
        assertThat(session.getCorrectAnswers()).isEqualTo(1);
        assertThat(session.getWrongAnswers()).isEqualTo(1); // javobsiz qolgan savol ham "noto'g'ri" hisoblanadi
        assertThat(session.getPercent()).isEqualTo(50);
    }

    @Test
    void finishTest_nullTotalQuestions_fallsBackToAnswersCount() {
        // Himoya: eski klient totalQuestions yubormasa ham (masalan
        // deploy vaqtida eski JS keshi), avvalgi xatti-harakat saqlanadi.
        user = freshUser();
        TestSession session = TestSession.builder().id(10L).user(user).questions(new ArrayList<>()).build();
        Answer correctAnswer = Answer.builder().id(100L).answerText("A").isTrue(true).build();
        Question q1 = Question.builder().id(1L).questionText("Q1").answers(List.of(correctAnswer)).build();

        when(testSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(q1));
        when(userQuestionStatsRepository.findById(any())).thenReturn(Optional.empty());

        FinishTestRequestDto request = new FinishTestRequestDto(10L,
                System.currentTimeMillis() - 60_000, System.currentTimeMillis(),
                List.of(new AnswerResultDto(1L, 100L)), null);

        testSessionService.finishTest(request, user);

        assertThat(session.getTotalQuestions()).isEqualTo(1);
    }

    @Test
    void finishTest_notOwnSession_throwsAccessDenied() {
        User owner = freshUser();
        User intruder = User.builder().id(2L).username("mallory").build();
        TestSession session = TestSession.builder().id(10L).user(owner).questions(new ArrayList<>()).build();
        when(testSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        FinishTestRequestDto request = new FinishTestRequestDto(10L, 1L, 2L, List.of(), 0);

        assertThatThrownBy(() -> testSessionService.finishTest(request, intruder))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void finishTest_sessionNotFound_throws() {
        when(testSessionRepository.findById(10L)).thenReturn(Optional.empty());
        FinishTestRequestDto request = new FinishTestRequestDto(10L, 1L, 2L, List.of(), 0);

        assertThatThrownBy(() -> testSessionService.finishTest(request, freshUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test session not found");
    }

    // ===== getHistory =====

    @Test
    void getHistory_wrapsPageResultIntoPageResponseDto() {
        user = freshUser();
        TestSessionHistoryDto dto = TestSessionHistoryDto.builder().testSessionId(1L).totalQuestions(5)
                .correctAnswers(4).percent(80).build();
        Page<TestSessionHistoryDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
        when(testSessionRepository.findByUserId(1L, PageRequest.of(0, 10))).thenReturn(page);

        PageResponseDto<TestSessionHistoryDto> result = testSessionService.getHistory(user, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
    }

    // ===== getDetails =====

    @Test
    void getDetails_mapsSelectedAndCorrectAnswerWithCommentary() {
        user = freshUser();
        TestSession session = TestSession.builder().id(10L).user(user).build();
        Question question = Question.builder().id(1L).questionText("2+2?").build();
        Answer selected = Answer.builder().id(1L).answerText("5").isTrue(false).build();
        Answer correct = Answer.builder().id(2L).answerText("4").isTrue(true).commentary("Yig'indi").build();
        question.setAnswers(List.of(selected, correct));

        TestSessionQuestion tsq = TestSessionQuestion.builder().id(1L).testSession(session)
                .question(question).selectedAnswer(selected).isCorrect(false).build();

        when(testSessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(testSessionQuestionRepository.findByTestSessionId(10L)).thenReturn(List.of(tsq));

        List<TestSessionDetailDto> result = testSessionService.getDetails(10L, user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).selectedAnswer()).isEqualTo("5");
        assertThat(result.get(0).correctAnswer()).isEqualTo("4");
        assertThat(result.get(0).commentOfCorrectAnswer()).isEqualTo("Yig'indi");
        assertThat(result.get(0).correct()).isFalse();
    }

    @Test
    void getDetails_notOwner_throwsAccessDenied() {
        when(testSessionRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testSessionService.getDetails(10L, freshUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== getStats =====

    @Test
    void getStats_computesAverageBestWorstAndTotalDuration() {
        user = freshUser();
        TestSession s1 = TestSession.builder().id(1L).user(user).percent(80).durationSec(60L)
                .finishedAt(LocalDateTime.now()).build();
        TestSession s2 = TestSession.builder().id(2L).user(user).percent(40).durationSec(90L)
                .finishedAt(LocalDateTime.now()).build();
        TestSession unfinished = TestSession.builder().id(3L).user(user).percent(0).durationSec(0L)
                .finishedAt(null).build();

        when(testSessionRepository.findByUserId(1L)).thenReturn(List.of(s1, s2, unfinished));

        TestStatsDto stats = testSessionService.getStats(user);

        assertThat(stats.totalTests()).isEqualTo(2); // faqat yakunlanganlar
        assertThat(stats.avgPercent()).isEqualTo(60);
        assertThat(stats.bestPercent()).isEqualTo(80);
        assertThat(stats.worstPercent()).isEqualTo(40);
        assertThat(stats.totalDurationSec()).isEqualTo(150L);
    }

    @Test
    void getStats_noFinishedSessions_returnsAllZeros() {
        user = freshUser();
        when(testSessionRepository.findByUserId(1L)).thenReturn(List.of());

        TestStatsDto stats = testSessionService.getStats(user);

        assertThat(stats.totalTests()).isZero();
        assertThat(stats.avgPercent()).isZero();
        assertThat(stats.bestPercent()).isZero();
        assertThat(stats.worstPercent()).isZero();
    }

    // ===== cancelTest =====

    @Test
    void cancelTest_ownedSession_deletesIt() {
        user = freshUser();
        TestSession session = TestSession.builder().id(10L).user(user).build();
        when(testSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        testSessionService.cancelTest(Map.of("testSessionId", 10L), user);

        verify(testSessionRepository).delete(session);
    }

    @Test
    void cancelTest_notOwned_doesNotDelete() {
        User owner = freshUser();
        User other = User.builder().id(2L).username("other").build();
        TestSession session = TestSession.builder().id(10L).user(owner).build();
        when(testSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        testSessionService.cancelTest(Map.of("testSessionId", 10L), other);

        verify(testSessionRepository, never()).delete(any());
    }

    @Test
    void cancelTest_sessionNotFound_doesNothingSilently() {
        when(testSessionRepository.findById(10L)).thenReturn(Optional.empty());

        testSessionService.cancelTest(Map.of("testSessionId", 10L), freshUser());

        verify(testSessionRepository, never()).delete(any());
    }
}
