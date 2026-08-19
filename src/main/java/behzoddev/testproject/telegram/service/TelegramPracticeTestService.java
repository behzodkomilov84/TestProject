package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.answer.AnswerDto;
import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.testsession.AnswerResultDto;
import behzoddev.testproject.dto.testsession.FinishTestRequestDto;
import behzoddev.testproject.dto.testsession.StartTestResponseDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.TestSession;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TestSessionService;
import behzoddev.testproject.service.TopicService;
import behzoddev.testproject.telegram.state.BotState;
import behzoddev.testproject.telegram.state.PracticeTestState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Botda "🎯 Mustaqil test" — fan tanlab, tasodifiy savollar bilan mashq
// (saytdagi /testConfigPage + testSession oqimining bot varianti).
// TestSessionService.startTest() savollarni faqat bir marta qaytaradi
// (bazada saqlamaydi) — shuning uchun bot davom etayotgan testning
// holatini (savollar, joriy indeks, tanlangan javoblar) o'zi
// TelegramSession.tempData'da (JSON) saqlab boradi.
@Service
@RequiredArgsConstructor
public class TelegramPracticeTestService {

    private static final String TEMP_KEY = "practiceTest";
    private static final List<Integer> COUNT_CANDIDATES = List.of(5, 10, 15, 20);

    private final ScienceService scienceService;
    private final TopicService topicService;
    private final TestSessionService testSessionService;
    private final TestSessionRepository testSessionRepository;
    private final UserRepository userRepository;
    private final TelegramSessionService sessionService;
    private final ObjectMapper objectMapper;

    // ================= 1. Fan tanlash =================

    public SendMessage startFlow(Long chatId) {
        List<ScienceIdAndNameDto> sciences = scienceService.getSciences();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (sciences.isEmpty()) {
            msg.setText("🎯 Hozircha fanlar mavjud emas.");
            return msg;
        }

        msg.setText("🎯 Mustaqil test uchun fanni tanlang:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ScienceIdAndNameDto science : sciences) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(science.name());
            btn.setCallbackData("pt_science_" + science.id());
            rows.add(List.of(btn));
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    // ================= 2. Savollar sonini tanlash =================

    public SendMessage selectScience(Long chatId, Long scienceId) {
        List<TopicWithQuestionCountDto> topics = topicService.getTopicsWithQuestionCount(scienceId);

        long available = topics.stream().mapToLong(TopicWithQuestionCountDto::questionCount).sum();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (available == 0) {
            msg.setText("🎯 Bu fanda hozircha savollar yo'q.");
            return msg;
        }

        List<Long> topicIds = topics.stream().map(TopicWithQuestionCountDto::id).toList();
        sessionService.putTempData(chatId, "pt_scienceId", scienceId.toString());
        sessionService.putTempData(chatId, "pt_topicIds", joinIds(topicIds));

        Set<Integer> options = new LinkedHashSet<>();
        for (Integer candidate : COUNT_CANDIDATES) {
            if (candidate <= available) options.add(candidate);
        }
        options.add((int) Math.min(available, Integer.MAX_VALUE));

        msg.setText("🎯 Nechta savol bilan mashq qilmoqchisiz? (jami mavjud: " + available + " ta)");

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Integer count : options) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(count + " ta");
            btn.setCallbackData("pt_count_" + count);
            row.add(btn);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        msg.setReplyMarkup(markup);
        return msg;
    }

    private String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) ids.add(Long.parseLong(part.trim()));
        }
        return ids;
    }

    // ================= 3. Testni boshlash =================

    public SendMessage startTest(Long chatId, int count) {
        User user = getUserByChatId(chatId);
        List<Long> topicIds = parseIds(sessionService.getTempData(chatId).get("pt_topicIds"));

        StartTestResponseDto response = testSessionService.startTest(user, topicIds, count, "normal");

        List<PracticeTestState.QuestionSnapshot> snapshots = response.questions().stream()
                .map(this::toSnapshot)
                .toList();

        PracticeTestState state = new PracticeTestState(
                response.testSessionId(),
                System.currentTimeMillis(),
                0,
                snapshots,
                new ArrayList<>()
        );

        sessionService.setState(chatId, BotState.IN_PRACTICE_TEST);
        saveState(chatId, state);

        return showQuestion(chatId, state);
    }

    private PracticeTestState.QuestionSnapshot toSnapshot(QuestionDto dto) {
        List<PracticeTestState.AnswerSnapshot> answers = dto.answers().stream()
                .map(a -> new PracticeTestState.AnswerSnapshot(a.id(), a.answerText(), a.imageUrl()))
                .toList();
        return new PracticeTestState.QuestionSnapshot(dto.id(), dto.questionText(), dto.imageUrl(), answers);
    }

    // ================= 4. Savolni ko'rsatish =================

    private SendMessage showQuestion(Long chatId, PracticeTestState state) {
        PracticeTestState.QuestionSnapshot q = state.questions().get(state.currentIndex());

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        StringBuilder sb = new StringBuilder();
        sb.append("❓ Savol ").append(state.currentIndex() + 1).append("/").append(state.questions().size())
                .append("\n\n").append(q.questionText()).append("\n\n");

        char option = 'A';
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (PracticeTestState.AnswerSnapshot a : q.answers()) {
            sb.append(option).append(") ").append(a.answerText()).append("\n");

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(String.valueOf(option));
            btn.setCallbackData("pt_answer_" + a.id());
            row.add(btn);

            option++;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row));

        msg.setText(sb.toString());
        msg.setReplyMarkup(markup);
        return msg;
    }

    // ================= 5. Javob tanlash =================

    public SendMessage submitAnswer(Long chatId, Long answerId) {
        PracticeTestState state = loadState(chatId);

        if (state == null) {
            // Sessiya tugagan/deploy bo'lgan bo'lishi mumkin — qaytadan boshlashni so'raymiz.
            sessionService.clear(chatId);
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("⚠️ Test sessiyasi topilmadi (ehtimol vaqt ko'p o'tgan). " +
                    "🎯 Mustaqil test tugmasidan qaytadan boshlang.");
            return msg;
        }

        PracticeTestState.QuestionSnapshot currentQuestion = state.questions().get(state.currentIndex());

        List<PracticeTestState.AnswerPick> answers = new ArrayList<>(state.answers());
        answers.add(new PracticeTestState.AnswerPick(currentQuestion.id(), answerId));

        int nextIndex = state.currentIndex() + 1;

        if (nextIndex >= state.questions().size()) {
            return finish(chatId, state, answers);
        }

        PracticeTestState next = new PracticeTestState(
                state.testSessionId(), state.startedAtEpochMilli(), nextIndex, state.questions(), answers);
        saveState(chatId, next);

        return showQuestion(chatId, next);
    }

    private SendMessage finish(Long chatId, PracticeTestState state, List<PracticeTestState.AnswerPick> answers) {
        User user = getUserByChatId(chatId);

        List<AnswerResultDto> results = answers.stream()
                .map(a -> new AnswerResultDto(a.questionId(), a.answerId()))
                .toList();

        long finishedAt = System.currentTimeMillis();

        testSessionService.finishTest(
                new FinishTestRequestDto(state.testSessionId(), state.startedAtEpochMilli(), finishedAt, results),
                user
        );

        sessionService.clear(chatId);

        TestSession session = testSessionRepository.findById(state.testSessionId()).orElse(null);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (session == null) {
            msg.setText("✅ Test yakunlandi!");
            return msg;
        }

        msg.setText("✅ <b>Test yakunlandi!</b>\n\n" +
                "⭐ Natija: " + session.getPercent() + "%\n" +
                "✔ To'g'ri: " + session.getCorrectAnswers() + "/" + session.getTotalQuestions() + "\n" +
                "⏱ Davomiylik: " + session.getDurationSec() + " soniya");
        msg.setParseMode("HTML");
        return msg;
    }

    // ================= Bekor qilish =================

    public SendMessage cancel(Long chatId) {
        sessionService.clear(chatId);
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("❎ Test bekor qilindi.");
        return msg;
    }

    public SendMessage reminderToUseButtons(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("👆 Iltimos, javobni yuqoridagi tugmalar orqali tanlang (yoki /cancel).");
        return msg;
    }

    // ================= Holatni saqlash/o'qish =================

    private void saveState(Long chatId, PracticeTestState state) {
        sessionService.putTempData(chatId, TEMP_KEY, objectMapper.writeValueAsString(state));
    }

    private PracticeTestState loadState(Long chatId) {
        String json = sessionService.getTempData(chatId).get(TEMP_KEY);
        if (json == null || json.isBlank()) return null;
        return objectMapper.readValue(json, PracticeTestState.class);
    }

    private User getUserByChatId(Long chatId) {
        return userRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }
}
