package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dao.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
                userRepository, sessionService, objectMapper);

        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();
        lenient().when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
    }

    // ===== startFlow =====

    @Test
    void startFlow_noSciences_saysEmpty() {
        when(scienceService.getSciences()).thenReturn(List.of());

        SendMessage msg = practiceTestService.startFlow(CHAT_ID);

        assertThat(msg.getText()).contains("fanlar mavjud emas");
    }

    @Test
    void startFlow_listsSciencesAsButtons() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));

        SendMessage msg = practiceTestService.startFlow(CHAT_ID);

        assertThat(msg.getText()).contains("fanni tanlang");
        assertThat(msg.getReplyMarkup()).isNotNull();
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
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 3L)));

        SendMessage msg = practiceTestService.selectScience(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("jami mavjud: 3");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void selectScience_manyQuestions_offersStandardCandidates() {
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 30L)));

        SendMessage msg = practiceTestService.selectScience(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("jami mavjud: 30");
    }

    // ===== to'liq oqim: startTest -> submitAnswer (bir nechta savol) -> natija =====

    @Test
    void fullFlow_twoQuestions_answeredCorrectlyAndIncorrectly_showsFinalResult() {
        // 1) fan tanlangan, topicIds saqlangan
        when(topicService.getTopicsWithQuestionCount(1L))
                .thenReturn(List.of(new TopicWithQuestionCountDto(10L, "Algebra", 2L)));
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

        when(testSessionService.startTest(any(), eq(List.of(10L)), eq(2), eq("normal")))
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

        assertThat(result.getText()).contains("50%").contains("1/2").contains("30 soniya");
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
}
