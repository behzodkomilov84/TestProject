package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.dto.answer.AnswerDto;
import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.testsession.StartTestResponseDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.TestSession;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TestSessionService;
import behzoddev.testproject.service.TopicService;
import behzoddev.testproject.telegram.dao.TelegramSessionRepository;
import behzoddev.testproject.telegram.entity.TelegramSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Botda "🎯 Mustaqil test" — fan tanlash -> savol soni tanlash -> savol-javob
 * -> natija. TestSessionService.startTest() savollarni faqat bir marta
 * qaytaradi (bazada saqlamaydi), shuning uchun bot ularni TelegramSession
 * orqali o'zi saqlab boradi — shu round-trip haqiqiy TelegramSessionService
 * (real JSON serializatsiya) bilan tekshiriladi, mock emas.
 */
@ExtendWith(MockitoExtension.class)
class TelegramPracticeTestServiceTest {

    private static final Long CHAT_ID = 777L;

    @Mock
    private ScienceService scienceService;
    @Mock
    private TopicService topicService;
    @Mock
    private TestSessionService testSessionService;
    @Mock
    private TestSessionRepository testSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TelegramSessionRepository telegramSessionRepository;

    private TelegramPracticeTestService practiceTestService;

    private TelegramSession stored;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        TelegramSessionService sessionService = new TelegramSessionService(telegramSessionRepository, objectMapper);

        lenient().when(telegramSessionRepository.save(any())).thenAnswer(inv -> {
            stored = inv.getArgument(0);
            return stored;
        });
        lenient().when(telegramSessionRepository.findById(CHAT_ID)).thenAnswer(inv -> Optional.ofNullable(stored));

        practiceTestService = new TelegramPracticeTestService(
                scienceService, topicService, testSessionService, testSessionRepository,
                userRepository, questionRepository, sessionService, objectMapper);

        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();
        lenient().when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
    }

    // ===== startFlow (rejim tanlash) =====

    @Test
    void startFlow_offersThreeModeButtons() {
        SendMessage msg = practiceTestService.startFlow(CHAT_ID);

        assertThat(msg.getText()).contains("rejimda mashq");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    // ===== selectMode (rejim tanlangandan keyin fan ro'yxati) =====

    @Test
    void selectMode_noSciences_saysEmpty() {
        when(scienceService.getSciences()).thenReturn(List.of());

        SendMessage msg = practiceTestService.selectMode(CHAT_ID, "practice");

        assertThat(msg.getText()).contains("bo'limlar mavjud emas");
    }

    @Test
    void selectMode_practice_listsSciencesAsButtons() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));

        SendMessage msg = practiceTestService.selectMode(CHAT_ID, "practice");

        assertThat(msg.getText()).contains("bo'limni tanlang");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void selectMode_storesModeForLaterSteps() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));

        practiceTestService.selectMode(CHAT_ID, "exam");

        assertThat(stored.getTempData()).contains("\"pt_mode\":\"exam\"");
    }

    // ===== Hard rejimi — faqat oldin xato qilingan savollar =====

    @Test
    void selectScience_hardMode_usesHardQuestionCount_notTotalQuestionCount() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "hard");

        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 100L, null, null, null)));
        when(questionRepository.findHardForUser(1L, List.of(10L)))
                .thenReturn(List.of(new Question(), new Question(), new Question()));

        SendMessage msg = practiceTestService.selectScience(CHAT_ID, 1L);

        // 100 emas — faqat 3 ta (hard) savol mavjud deb ko'rsatilishi kerak.
        assertThat(msg.getText()).contains("jami mavjud: 3");
    }

    @Test
    void selectScience_hardMode_noHardQuestions_saysEmptyWithHardSpecificMessage() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "hard");

        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 100L, null, null, null)));
        when(questionRepository.findHardForUser(1L, List.of(10L))).thenReturn(List.of());

        SendMessage msg = practiceTestService.selectScience(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("xato qilingan");
    }

    // ===== startForTopic (kurs bo'limidagi "🎯 Mavzuga oid testlarni
    // yechish" tugmasidan — rejim/fan tanlashni o'tkazib yuborib,
    // to'g'ridan-to'g'ri savol soni tanlashga o'tadi) =====

    @Test
    void startForTopic_noQuestions_saysEmpty() {
        when(questionRepository.countByTopicIds(List.of(10L))).thenReturn(0);

        SendMessage msg = practiceTestService.startForTopic(CHAT_ID, 10L);

        assertThat(msg.getText()).contains("savollar yo'q");
    }

    @Test
    void startForTopic_hasQuestions_offersCountSelectionDirectly() {
        when(questionRepository.countByTopicIds(List.of(10L))).thenReturn(30);

        SendMessage msg = practiceTestService.startForTopic(CHAT_ID, 10L);

        // Fan tanlash bosqichi o'tkazib yuborilgan — darhol savol soni so'raladi.
        assertThat(msg.getText()).contains("jami mavjud: 30");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void startForTopic_thenChooseCount_startsPracticeTestForThatTopicOnly() {
        when(questionRepository.countByTopicIds(List.of(10L))).thenReturn(5);
        practiceTestService.startForTopic(CHAT_ID, 10L);

        QuestionDto question = new QuestionDto(1L, "Savol?", null,
                List.of(new AnswerDto(1L, "Javob", true, null, null, null, null)));
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(5), eq("practice")))
                .thenReturn(new StartTestResponseDto(99L, List.of(question)));

        SendMessage msg = practiceTestService.chooseCount(CHAT_ID, 5);

        assertThat(msg.getText()).contains("Savol?");
    }

    // ===== selectScience =====

    @Test
    void selectScience_noQuestions_saysEmpty() {
        when(topicService.getTopicsWithQuestionCount(1L)).thenReturn(List.of());

        SendMessage msg = practiceTestService.selectScience(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("savollar yo'q");
    }

    @Test
    void selectScience_fewQuestions_offersOnlyAvailableCount() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 3L, null, null, null)));

        SendMessage msg = practiceTestService.selectScience(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("jami mavjud: 3");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void selectScience_manyQuestions_offersStandardCandidates() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 30L, null, null, null)));

        SendMessage msg = practiceTestService.selectScience(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("jami mavjud: 30");
    }

    // ===== to'liq oqim: startTest -> submitAnswer (bir nechta savol) -> natija =====

    @Test
    void fullFlow_twoQuestions_answeredCorrectlyAndIncorrectly_showsFinalResult() {
        // 1) fan tanlangan, topicIds saqlangan
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 2L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);

        // 2) savol soni tanlanadi -> testSessionService.startTest chaqiriladi
        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("2+2=?").imageUrl(null)
                .answers(List.of(
                        new AnswerDto(1000L, "3", false, "yo'q", null, null, null),
                        new AnswerDto(1001L, "4", true, "to'g'ri", null, null, null)
                )).build();
        QuestionDto q2 = QuestionDto.builder().id(101L).questionText("3+3=?").imageUrl(null)
                .answers(List.of(
                        new AnswerDto(1002L, "6", true, "to'g'ri", null, null, null),
                        new AnswerDto(1003L, "7", false, "yo'q", null, null, null)
                )).build();

        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(2), eq("practice")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1, q2)));

        SendMessage firstQuestion = practiceTestService.startTest(CHAT_ID, 2);

        assertThat(firstQuestion.getText()).contains("Savol 1/2").contains("2+2=?");

        // 3) 1-savolga NOTO'G'RI javob (id=1000, "3")
        SendMessage secondQuestion = practiceTestService.submitAnswer(CHAT_ID, 1000L);

        assertThat(secondQuestion.getText()).contains("Savol 2/2").contains("3+3=?");

        // 4) 2-savolga TO'G'RI javob (id=1002, "6") -> test yakunlanadi
        TestSession finishedSession = TestSession.builder()
                .id(999L).totalQuestions(2).correctAnswers(1).wrongAnswers(1)
                .percent(50).durationSec(30L).build();
        when(testSessionRepository.findById(999L)).thenReturn(Optional.of(finishedSession));

        SendMessage result = practiceTestService.submitAnswer(CHAT_ID, 1002L);

        verify(testSessionService).finishTest(argThat(req ->
                req.testSessionId().equals(999L) &&
                        req.answers().size() == 2 &&
                        req.answers().get(0).questionId().equals(100L) &&
                        req.answers().get(0).answerId().equals(1000L) &&
                        req.answers().get(1).questionId().equals(101L) &&
                        req.answers().get(1).answerId().equals(1002L)
        ), any());

        assertThat(result.getText()).contains("50%").contains("1/2").contains("30 soniya")
                .contains("Practice"); // hech qanday rejim tanlanmagan bo'lsa, standart — practice.
    }

    @Test
    void startTest_examMode_passesExamModeToTestSessionService() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "exam");
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);

        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(3), eq("exam")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));

        practiceTestService.startTest(CHAT_ID, 3);

        verify(testSessionService).startTest(any(), eq(List.of(10L)), eq(3), eq("exam"));
    }

    @Test
    void startTest_hardMode_passesHardModeToTestSessionService() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "hard");
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        when(questionRepository.findHardForUser(1L, List.of(10L)))
                .thenReturn(List.of(new Question(), new Question()));
        practiceTestService.selectScience(CHAT_ID, 1L);

        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(2), eq("hard")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));

        practiceTestService.startTest(CHAT_ID, 2);

        verify(testSessionService).startTest(any(), eq(List.of(10L)), eq(2), eq("hard"));
    }

    @Test
    void submitAnswer_noActiveState_returnsFriendlyErrorAndClearsSession() {
        SendMessage msg = practiceTestService.submitAnswer(CHAT_ID, 123L);

        assertThat(msg.getText()).contains("Test sessiyasi topilmadi");
    }

    // ===== cancel / reminder =====

    @Test
    void cancel_clearsSessionAndConfirms() {
        SendMessage msg = practiceTestService.cancel(CHAT_ID);

        assertThat(msg.getText()).contains("bekor qilindi");
        verify(telegramSessionRepository).save(any());
    }

    @Test
    void reminderToUseButtons_returnsHintText() {
        SendMessage msg = practiceTestService.reminderToUseButtons(CHAT_ID);

        assertThat(msg.getText()).contains("tugmalar orqali");
    }

    // ===== O'zi kiritish (custom savol soni) =====

    @Test
    void promptCustomCount_asksForNumberWithAvailableRange() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 37L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);

        SendMessage msg = practiceTestService.promptCustomCount(CHAT_ID);

        assertThat(msg.getText()).contains("1 dan").contains("37");
    }

    @Test
    void applyCustomCount_validNumber_startsTestWithThatCount() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 37L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);
        practiceTestService.promptCustomCount(CHAT_ID);

        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(17), eq("practice")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));

        SendMessage msg = practiceTestService.applyCustomCount(CHAT_ID, "17");

        assertThat(msg.getText()).contains("Savol 1/1");
        verify(testSessionService).startTest(any(), eq(List.of(10L)), eq(17), eq("practice"));
    }

    @Test
    void applyCustomCount_notANumber_retriesWithoutStartingTest() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 37L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);
        practiceTestService.promptCustomCount(CHAT_ID);

        SendMessage msg = practiceTestService.applyCustomCount(CHAT_ID, "abc");

        assertThat(msg.getText()).contains("butun son");
        verify(testSessionService, never()).startTest(any(), any(), anyInt(), any());
    }

    @Test
    void applyCustomCount_outOfRange_retriesWithoutStartingTest() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 37L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);
        practiceTestService.promptCustomCount(CHAT_ID);

        SendMessage msg = practiceTestService.applyCustomCount(CHAT_ID, "500");

        assertThat(msg.getText()).contains("1 dan").contains("37");
        verify(testSessionService, never()).startTest(any(), any(), anyInt(), any());
    }

    @Test
    void applyCustomCount_zeroOrNegative_retriesWithoutStartingTest() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 37L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);
        practiceTestService.promptCustomCount(CHAT_ID);

        SendMessage msg = practiceTestService.applyCustomCount(CHAT_ID, "0");

        assertThat(msg.getText()).contains("1 dan");
        verify(testSessionService, never()).startTest(any(), any(), anyInt(), any());
    }

    // ===== Vaqt chegarasi (Exam/Hard) — saytdagi timeSection bilan bir xil =====

    @Test
    void chooseCount_practiceMode_startsTestImmediately_noTimePrompt() {
        // rejim tanlanmagan (default — practice)
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);

        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(3), eq("practice")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));

        SendMessage msg = practiceTestService.chooseCount(CHAT_ID, 3);

        assertThat(msg.getText()).contains("Savol 1/1");
        verify(testSessionService).startTest(any(), eq(List.of(10L)), eq(3), eq("practice"));
    }

    @Test
    void chooseCount_examMode_promptsForTimeLimitInsteadOfStartingTest() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "exam");
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);

        SendMessage msg = practiceTestService.chooseCount(CHAT_ID, 3);

        assertThat(msg.getText()).contains("vaqtni tanlang");
        assertThat(msg.getReplyMarkup()).isNotNull();
        verify(testSessionService, never()).startTest(any(), any(), anyInt(), any());
    }

    @Test
    void applyTimeLimit_examMode_startsTestWithDeadline_showsRemainingTimeInFirstQuestion() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "exam");
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);
        practiceTestService.chooseCount(CHAT_ID, 3);

        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(3), eq("exam")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));

        SendMessage msg = practiceTestService.applyTimeLimit(CHAT_ID, 20);

        assertThat(msg.getText()).contains("qolgan vaqt");
    }

    @Test
    void promptCustomTimeLimit_setsAwaitingState() {
        practiceTestService.promptCustomTimeLimit(CHAT_ID);

        assertThat(stored.getState()).isEqualTo("AWAITING_PT_CUSTOM_TIME");
    }

    @Test
    void applyCustomTimeLimit_notANumber_retriesWithoutStartingTest() {
        SendMessage msg = practiceTestService.applyCustomTimeLimit(CHAT_ID, "abc");

        assertThat(msg.getText()).contains("butun son");
        verify(testSessionService, never()).startTest(any(), any(), anyInt(), any());
    }

    @Test
    void applyCustomTimeLimit_outOfRange_retriesWithoutStartingTest() {
        SendMessage msg = practiceTestService.applyCustomTimeLimit(CHAT_ID, "999");

        assertThat(msg.getText()).contains("1 dan").contains("180");
        verify(testSessionService, never()).startTest(any(), any(), anyInt(), any());
    }

    @Test
    void applyCustomTimeLimit_valid_startsTestWithThatDeadline() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "hard");
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        when(questionRepository.findHardForUser(1L, List.of(10L)))
                .thenReturn(List.of(new Question(), new Question()));
        practiceTestService.selectScience(CHAT_ID, 1L);
        practiceTestService.chooseCount(CHAT_ID, 2);

        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(2), eq("hard")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));

        SendMessage msg = practiceTestService.applyCustomTimeLimit(CHAT_ID, "45");

        assertThat(msg.getText()).contains("qolgan vaqt");
    }

    // ===== Vaqt tugagach avtomatik yakunlash =====

    @Test
    void submitAnswer_deadlineAlreadyExpired_autoFinishesWithTimeoutNotice() {
        startExamTestWithOneQuestion();
        expireCurrentDeadline();

        TestSession finishedSession = TestSession.builder()
                .id(999L).totalQuestions(1).correctAnswers(0).wrongAnswers(1)
                .percent(0).durationSec(1200L).build();
        when(testSessionRepository.findById(999L)).thenReturn(Optional.of(finishedSession));

        SendMessage msg = practiceTestService.submitAnswer(CHAT_ID, 1000L);

        assertThat(msg.getText()).contains("Vaqt tugadi").contains("avtomatik yakunlandi");
        // shu javob (1000L) hisoblanmasligi kerak — deadline allaqachon tugagan edi.
        verify(testSessionService).finishTest(argThat(req -> req.answers().isEmpty()), any());
    }

    @Test
    void autoFinishIfExpired_noDeadline_returnsNull() {
        // practice rejimi — deadline yo'q
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);
        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(1), eq("practice")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));
        practiceTestService.startTest(CHAT_ID, 1);

        SendMessage msg = practiceTestService.autoFinishIfExpired(CHAT_ID);

        assertThat(msg).isNull();
    }

    @Test
    void autoFinishIfExpired_noActiveTest_returnsNull() {
        SendMessage msg = practiceTestService.autoFinishIfExpired(CHAT_ID);

        assertThat(msg).isNull();
    }

    @Test
    void autoFinishIfExpired_deadlinePassed_autoFinishesWithTimeoutNotice() {
        startExamTestWithOneQuestion();
        expireCurrentDeadline();

        TestSession finishedSession = TestSession.builder()
                .id(999L).totalQuestions(1).correctAnswers(0).wrongAnswers(1)
                .percent(0).durationSec(1200L).build();
        when(testSessionRepository.findById(999L)).thenReturn(Optional.of(finishedSession));

        SendMessage msg = practiceTestService.autoFinishIfExpired(CHAT_ID);

        assertThat(msg).isNotNull();
        assertThat(msg.getText()).contains("Vaqt tugadi").contains("avtomatik yakunlandi");
    }

    // ===== Yordamchilar =====

    private void startExamTestWithOneQuestion() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));
        practiceTestService.selectMode(CHAT_ID, "exam");
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 5L, null, null, null)));
        practiceTestService.selectScience(CHAT_ID, 1L);
        practiceTestService.chooseCount(CHAT_ID, 1);

        QuestionDto q1 = QuestionDto.builder().id(100L).questionText("Savol?").imageUrl(null)
                .answers(List.of(new AnswerDto(1000L, "A", true, null, null, null, null))).build();
        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(1), eq("exam")))
                .thenReturn(new StartTestResponseDto(999L, List.of(q1)));

        practiceTestService.applyTimeLimit(CHAT_ID, 30);
    }

    // stored.tempData ichidagi "practiceTest" JSON'i ichiga kirib,
    // deadlineEpochMilli'ni o'tmishga suradi — deadline'ning haqiqatan
    // ham hurmat qilinishini (JSON round-trip bilan) tekshirish uchun.
    @SuppressWarnings("unchecked")
    private void expireCurrentDeadline() {
        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, String> outer = mapper.readValue(stored.getTempData(), Map.class);
        Map<String, Object> inner = mapper.readValue(outer.get("practiceTest"), Map.class);
        inner.put("deadlineEpochMilli", System.currentTimeMillis() - 1000);
        outer.put("practiceTest", mapper.writeValueAsString(inner));
        stored.setTempData(mapper.writeValueAsString(outer));
    }
}
