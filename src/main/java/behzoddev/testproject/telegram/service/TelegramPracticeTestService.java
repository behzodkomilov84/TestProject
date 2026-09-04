package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.QuestionRepository;
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

// Botda "🎯 Mustaqil test" — bo'lim tanlab, tasodifiy savollar bilan mashq
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
    private static final List<Integer> TIME_CANDIDATES_MIN = List.of(10, 20, 30, 60);
    private static final int MAX_TIME_LIMIT_MIN = 180;
    private static final String DEFAULT_MODE = "practice";
    // Bo'lim/Mavzu/Dars ro'yxatlari endi RAQAMLI tugmalar bilan, gorizontal
    // (yonma-yon) katakcha ko'rinishida — to'liq nomlar xabar MATNIDA
    // ko'rsatiladi (TelegramCourseReaderService'dagi bilan bir xil g'oya,
    // uzun ro'yxatda tugmalar bir-birining ustiga chiqib, bir qismi
    // ko'rinmay qolish muammosini oldini oladi).
    private static final int BUTTONS_PER_ROW = 4;
    // Ro'yxat bandlari orasidagi ajratuvchi chiziq — nomlar uzun bo'lganda
    // (ayniqsa ko'p qatorli) bandlar bir-biriga "yopishib" ketmasligi uchun
    // (foydalanuvchi so'rovi bo'yicha).
    private static final String LIST_SEPARATOR = "➖➖➖➖➖➖➖➖➖➖\n";

    private final ScienceService scienceService;
    private final TopicService topicService;
    private final TestSessionService testSessionService;
    private final TestSessionRepository testSessionRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final TelegramSessionService sessionService;
    private final ObjectMapper objectMapper;

    // ================= 0. Rejimni tanlash =================
    // Saytdagi bosh sahifadagi uchta tugma (Practice/Exam/Hard Mode) bilan
    // bir xil — /testConfigPage'ga o'tishdan oldin rejim tanlanadi. Botda
    // ham xuddi shu tartib: bo'lim tanlashdan OLDIN rejim so'raladi.

    public SendMessage startFlow(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("🎯 Qanday rejimda mashq qilmoqchisiz?\n\n" +
                "📝 Practice — vaqt chegarasiz, tinch mashq.\n" +
                "⏱ Exam — imtihon rejimida sinab ko'rish.\n" +
                "🔥 Hard — faqat avval XATO javob bergan savollaringiz.");

        InlineKeyboardButton practiceBtn = button("📝 Practice", "pt_mode_practice");
        InlineKeyboardButton examBtn = button("⏱ Exam", "pt_mode_exam");
        InlineKeyboardButton hardBtn = button("🔥 Hard", "pt_mode_hard");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(practiceBtn, examBtn), List.of(hardBtn)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    // Tekis tugmalar ro'yxatini BUTTONS_PER_ROW tadan qatorlarga bo'ladi
    // (TelegramCourseReaderService'dagi bilan bir xil g'oya).
    private List<List<InlineKeyboardButton>> chunkIntoRows(List<InlineKeyboardButton> buttons) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += BUTTONS_PER_ROW) {
            rows.add(new ArrayList<>(buttons.subList(i, Math.min(i + BUTTONS_PER_ROW, buttons.size()))));
        }
        return rows;
    }

    // ================= 1. Bo'lim tanlash =================

    public SendMessage selectMode(Long chatId, String mode) {
        sessionService.putTempData(chatId, "pt_mode", mode);
        return showScienceSelection(chatId);
    }

    private SendMessage showScienceSelection(Long chatId) {
        String mode = currentMode(chatId);
        List<ScienceIdAndNameDto> sciences = scienceService.getSciences();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (sciences.isEmpty()) {
            msg.setText(modeLabel(mode) + " rejimi tanlandi.\n\n🎯 Hozircha bo'limlar mavjud emas.");
            return msg;
        }

        // Foydalanuvchi qaysi rejimni tanlaganini aniq ko'rishi uchun —
        // ilgari bu xabar berilmasdi, "bo'limni tanlang" faqat shunday chiqardi.
        // To'liq bo'lim nomlari matnda RAQAMLANGAN ro'yxat sifatida, tugmalar
        // esa faqat mos raqam bilan (gorizontal katakcha).
        StringBuilder sb = new StringBuilder(modeLabel(mode) + " rejimi tanlandi.\n\n🎯 Mustaqil test uchun bo'limni tanlang:\n\n");
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        int i = 1;
        for (ScienceIdAndNameDto science : sciences) {
            if (i > 1) sb.append(LIST_SEPARATOR);
            sb.append(i).append(". ").append(science.name()).append("\n");
            buttons.add(button(String.valueOf(i), "pt_science_" + science.id()));
            i++;
        }

        msg.setText(sb.toString());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>(chunkIntoRows(buttons));
        rows.add(List.of(button("🔙 Orqaga", "pt_back_mode")));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    // "🔙 Orqaga" tugmasi — rejim tanlash (bo'lim ro'yxatidan bir qadam orqaga).
    public SendMessage backToScienceSelection(Long chatId) {
        return showScienceSelection(chatId);
    }

    // Kurs darsidagi "🎯 Darsga oid testlarni yechish" tugmasidan —
    // rejim/bo'lim tanlashni o'tkazib yuborib, to'g'ridan-to'g'ri shu BITTA
    // dars bo'yicha (Practice rejimida, vaqt chegarasisiz — tezda yechish
    // uchun qulay) savol sonini tanlashga o'tadi.
    public SendMessage startForTopic(Long chatId, Long topicId) {
        sessionService.putTempData(chatId, "pt_mode", DEFAULT_MODE);

        List<Long> topicIds = List.of(topicId);
        long available = questionRepository.countByTopicIds(topicIds);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (available == 0) {
            msg.setText("🎯 Bu darsda hozircha savollar yo'q.");
            return msg;
        }

        sessionService.putTempData(chatId, "pt_topicIds", joinIds(topicIds));
        sessionService.putTempData(chatId, "pt_available", String.valueOf(available));

        Set<Integer> options = new LinkedHashSet<>();
        for (Integer candidate : COUNT_CANDIDATES) {
            if (candidate <= available) options.add(candidate);
        }
        options.add((int) Math.min(available, Integer.MAX_VALUE));

        msg.setText("🎯 Darsga oid testlar — nechta savol bilan mashq qilmoqchisiz? (jami mavjud: " + available + " ta)");

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Integer count : options) {
            row.add(button(count + " ta", "pt_count_" + count));
        }
        InlineKeyboardButton customBtn = button("✏️ O'zi kiritish", "pt_count_custom");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row, List.of(customBtn)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    // ================= 1.5. Mavzu va Dars tanlash =================
    // Saytdagi testConfigPage.js bilan bir xil mantiq: agar shu bo'limda
    // Mavzu(lar) bo'lsa (masalan Kimyo — "I. UMUMIY KIMYO" va h.k.),
    // avval Mavzu, keyin (ixtiyoriy) Dars tanlanadi. Mavzusi yo'q
    // (yoki umuman Mavzularga bo'linmagan) bo'limlarda bu qadam butunlay
    // o'tkazib yuboriladi — to'g'ridan-to'g'ri eskicha, butun bo'lim
    // bo'yicha son tanlashga o'tiladi (100% orqaga moslik).

    public SendMessage selectScience(Long chatId, Long scienceId) {
        sessionService.putTempData(chatId, "pt_scienceId", scienceId.toString());
        List<TopicWithQuestionCountDto> topics = topicService.getTopicsWithQuestionCount(scienceId);

        // Mavzu(lar)ga bo'linmagan (yoki darsi umuman yo'q) bo'lim uchun —
        // eskicha, to'g'ridan-to'g'ri butun bo'lim bo'yicha son tanlashga
        // o'tiladi (showCountSelection bo'sh ro'yxatni ham to'g'ri
        // "savollar yo'q" xabari bilan qayta ishlaydi).
        boolean hasSections = topics.stream().anyMatch(t -> t.sectionId() != null);
        if (!hasSections) {
            return showCountSelection(chatId, topics, "bo'limda");
        }

        return showSectionSelection(chatId, scienceId, topics);
    }

    private SendMessage showSectionSelection(Long chatId, Long scienceId, List<TopicWithQuestionCountDto> topics) {
        // sectionId -> {name, orderIndex} — LinkedHashMap emas, tartib
        // orderIndex bo'yicha alohida saralanadi (topics ro'yxati
        // TopicRepository.getTopicsWithQuestionCount'da allaqachon shunday
        // tartiblangan, lekin bu yerda ANIQ qilish uchun qayta saralanadi).
        record SectionInfo(Long id, String name, Integer orderIndex) {}
        java.util.Map<Long, SectionInfo> sectionsById = new java.util.LinkedHashMap<>();
        boolean hasUnassigned = false;
        for (TopicWithQuestionCountDto t : topics) {
            if (t.sectionId() != null) {
                sectionsById.putIfAbsent(t.sectionId(), new SectionInfo(t.sectionId(), t.sectionName(), t.sectionOrderIndex()));
            } else {
                hasUnassigned = true;
            }
        }

        List<SectionInfo> sorted = sectionsById.values().stream()
                .sorted(java.util.Comparator.comparing(SectionInfo::orderIndex,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();

        // Mavzu nomlari matnda raqamlangan ro'yxat sifatida, tugmalar esa
        // faqat mos raqam bilan (gorizontal katakcha) — "Mavzusiz
        // darslar" va "Barchasi" ham ketma-ket raqamga qo'shiladi.
        StringBuilder sb = new StringBuilder("📂 Mavzuni tanlang:\n\n");
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        int i = 1;
        for (SectionInfo sec : sorted) {
            if (i > 1) sb.append(LIST_SEPARATOR);
            sb.append(i).append(". ").append(sec.name()).append("\n");
            buttons.add(button(String.valueOf(i), "pt_section_" + scienceId + "_" + sec.id()));
            i++;
        }
        if (hasUnassigned) {
            if (i > 1) sb.append(LIST_SEPARATOR);
            sb.append(i).append(". — Mavzusiz darslar —\n");
            buttons.add(button(String.valueOf(i), "pt_section_" + scienceId + "_none"));
            i++;
        }
        if (i > 1) sb.append(LIST_SEPARATOR);
        sb.append(i).append(". 🔷 Barchasi (shu bo'lim bo'yicha)\n");
        buttons.add(button(String.valueOf(i), "pt_section_" + scienceId + "_all"));

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>(chunkIntoRows(buttons));
        rows.add(List.of(button("🔙 Orqaga", "pt_back_science")));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    // "pt_section_<scienceId>_<sectionValue>" — sectionValue "all" (butun
    // bo'lim, eskicha xulq-atvor), "none" (mavzusiz darslar) yoki haqiqiy
    // mavzu id'si bo'lishi mumkin.
    public SendMessage selectSection(Long chatId, Long scienceId, String sectionValue) {
        List<TopicWithQuestionCountDto> allTopics = topicService.getTopicsWithQuestionCount(scienceId);

        if ("all".equals(sectionValue)) {
            return showCountSelection(chatId, allTopics, "bo'limda");
        }

        List<TopicWithQuestionCountDto> filtered;
        String sectionLabel;
        if ("none".equals(sectionValue)) {
            filtered = allTopics.stream().filter(t -> t.sectionId() == null).toList();
            sectionLabel = "Mavzusiz darslar";
        } else {
            Long sectionId = Long.parseLong(sectionValue);
            filtered = allTopics.stream().filter(t -> sectionId.equals(t.sectionId())).toList();
            sectionLabel = filtered.isEmpty() ? "Mavzu" : filtered.get(0).sectionName();
        }

        if (filtered.isEmpty()) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("🎯 Bu mavzuda hozircha darslar mavjud emas.");
            return msg;
        }

        return showTopicSelection(chatId, scienceId, sectionValue, sectionLabel, filtered);
    }

    private SendMessage showTopicSelection(Long chatId, Long scienceId, String sectionValue, String sectionLabel,
                                            List<TopicWithQuestionCountDto> topics) {
        // Dars nomlari matnda raqamlangan ro'yxat sifatida, tugmalar
        // faqat mos raqam bilan (gorizontal katakcha) — avval har bir
        // dars to'liq nomi bilan alohida qatorda edi, uzun ro'yxatlarda
        // (masalan 15+ dars) bir qismi ko'rinmay qolardi.
        StringBuilder sb = new StringBuilder("📖 " + sectionLabel + " — darsni tanlang:\n\n");
        sb.append("🔷 — Barcha darslar (shu mavzuda)\n");
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(button("🔷", "pt_topicgroup_" + scienceId + "_" + sectionValue));

        int i = 1;
        for (TopicWithQuestionCountDto t : topics) {
            sb.append(LIST_SEPARATOR);
            sb.append(i).append(". ").append(t.name()).append(" (").append(t.questionCount()).append(" ta)\n");
            buttons.add(button(String.valueOf(i), "pt_topic_" + t.id()));
            i++;
        }

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>(chunkIntoRows(buttons));
        rows.add(List.of(button("🔙 Orqaga", "pt_back_section_" + scienceId)));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    // "🔙 Orqaga" tugmasi — dars ro'yxatidan mavzu ro'yxatiga qaytish.
    public SendMessage backToSectionSelection(Long chatId, Long scienceId) {
        List<TopicWithQuestionCountDto> topics = topicService.getTopicsWithQuestionCount(scienceId);
        return showSectionSelection(chatId, scienceId, topics);
    }

    // "🔷 Barcha darslar (shu mavzuda)" tugmasi — selectSection bilan bir
    // xil filtrlash, lekin dars tanlashni o'tkazib, darhol son tanlashga
    // o'tadi.
    public SendMessage selectTopicGroup(Long chatId, Long scienceId, String sectionValue) {
        List<TopicWithQuestionCountDto> allTopics = topicService.getTopicsWithQuestionCount(scienceId);

        List<TopicWithQuestionCountDto> filtered;
        if ("none".equals(sectionValue)) {
            filtered = allTopics.stream().filter(t -> t.sectionId() == null).toList();
        } else {
            Long targetSectionId = Long.valueOf(sectionValue);
            filtered = allTopics.stream().filter(t -> targetSectionId.equals(t.sectionId())).toList();
        }

        return showCountSelection(chatId, filtered, "mavzuda");
    }

    // Bitta aniq dars tanlanganda (darslar ro'yxatidan) — DIQQAT:
    // startForTopic()'dan farqli, bu yerda foydalanuvchi allaqachon
    // tanlagan rejim (practice/exam/hard) SAQLANIB QOLADI (startForTopic
    // esa kurs darsidan to'g'ridan-to'g'ri kelinganda, rejim tanlash
    // qadami umuman bo'lmagani uchun har doim "practice"ga qat'iy
    // belgilaydi).
    public SendMessage selectTopic(Long chatId, Long topicId) {
        String scienceIdRaw = sessionService.getTempData(chatId).get("pt_scienceId");
        List<TopicWithQuestionCountDto> topics = scienceIdRaw == null || scienceIdRaw.isBlank()
                ? List.of()
                : topicService.getTopicsWithQuestionCount(Long.parseLong(scienceIdRaw));

        TopicWithQuestionCountDto match = topics.stream()
                .filter(t -> topicId.equals(t.id()))
                .findFirst()
                .orElse(null);

        if (match == null) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Dars topilmadi. 🎯 Mustaqil test tugmasidan qaytadan boshlang.");
            return msg;
        }

        return showCountSelection(chatId, List.of(match), "darsda");
    }

    // ================= 2. Savollar sonini tanlash =================
    // contextLabel — "bo'limda"/"mavzuda"/"darsda" — savol topilmasa
    // chiqadigan xabarga qo'yiladi ("Bu <contextLabel> hozircha ... yo'q").
    // "topics" (raw id ro'yxati emas) qabul qilinadi — questionCount()
    // ALLAQACHON olib kelingan bo'lgani uchun qayta so'rov yuborilmaydi
    // (asl selectScience shunday ishlagan, testlar ham shunga mos).
    private SendMessage showCountSelection(Long chatId, List<TopicWithQuestionCountDto> topics, String contextLabel) {
        String mode = currentMode(chatId);
        List<Long> topicIds = topics.stream().map(TopicWithQuestionCountDto::id).toList();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        long available;
        if ("hard".equals(mode)) {
            // Hard rejimi — saytdagidek, faqat shu foydalanuvchi avval
            // XATO javob bergan savollar hisobga olinadi (barcha savollar
            // soni emas).
            User user = getUserByChatId(chatId);
            available = questionRepository.findHardForUser(user.getId(), topicIds).size();
            if (available == 0) {
                msg.setText("🔥 Bu " + contextLabel + " hozircha xato qilingan (hard) savollaringiz yo'q.");
                return msg;
            }
        } else {
            available = topics.stream().mapToLong(TopicWithQuestionCountDto::questionCount).sum();
            if (available == 0) {
                msg.setText("🎯 Bu " + contextLabel + " hozircha savollar yo'q.");
                return msg;
            }
        }

        sessionService.putTempData(chatId, "pt_topicIds", joinIds(topicIds));
        sessionService.putTempData(chatId, "pt_available", String.valueOf(available));

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

        InlineKeyboardButton customBtn = new InlineKeyboardButton();
        customBtn.setText("✏️ O'zi kiritish");
        customBtn.setCallbackData("pt_count_custom");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row, List.of(customBtn)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    // Foydalanuvchi "✏️ O'zi kiritish" tugmasini bosgach — istagan sonni
    // qo'lda yozib yuborishini so'raymiz.
    public SendMessage promptCustomCount(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_PT_CUSTOM_COUNT);

        long available = parseAvailable(chatId);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("✏️ Nechta savol bilan mashq qilishni xohlaysiz? Sonini yozib yuboring " +
                "(1 dan " + available + " tagacha).");
        return msg;
    }

    // Foydalanuvchi qo'lda yozgan sonni qabul qiladi — to'g'ri son bo'lmasa
    // yoki mavjud savollar sonidan oshib ketsa, qaytadan so'raymiz.
    public SendMessage applyCustomCount(Long chatId, String text) {
        long available = parseAvailable(chatId);

        int count;
        try {
            count = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Iltimos, faqat butun son kiriting (masalan: 12).");
            return msg;
        }

        if (count < 1 || count > available) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Son 1 dan " + available + " tagacha bo'lishi kerak. Qaytadan yozing.");
            return msg;
        }

        return chooseCount(chatId, count);
    }

    // Savollar soni tanlangandan keyingi qadam — Exam/Hard rejimida
    // saytdagidek vaqt chegarasi ham so'raladi, Practice rejimida
    // (vaqt chegarasisiz) test darhol boshlanadi.
    public SendMessage chooseCount(Long chatId, int count) {
        String mode = currentMode(chatId);

        if (needsTimeLimit(mode)) {
            sessionService.putTempData(chatId, "pt_count", String.valueOf(count));
            return promptTimeLimit(chatId);
        }

        return startTest(chatId, count, null);
    }

    private boolean needsTimeLimit(String mode) {
        return "exam".equals(mode) || "hard".equals(mode);
    }

    // ================= 2.5. Vaqt chegarasini tanlash (Exam/Hard) =================

    private SendMessage promptTimeLimit(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("⏱ Test uchun umumiy vaqtni tanlang (daqiqa). " +
                "Vaqt tugasa, test avtomatik yakunlanadi (saytdagi bilan bir xil):");

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Integer minutes : TIME_CANDIDATES_MIN) {
            row.add(button(minutes + " daq", "pt_time_" + minutes));
        }
        InlineKeyboardButton customBtn = button("✏️ O'zi kiritish", "pt_time_custom");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row, List.of(customBtn)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage promptCustomTimeLimit(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_PT_CUSTOM_TIME);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("✏️ Necha daqiqa vaqt bermoqchisiz? Sonini yozib yuboring " +
                "(1 dan " + MAX_TIME_LIMIT_MIN + " tagacha).");
        return msg;
    }

    public SendMessage applyTimeLimit(Long chatId, int minutes) {
        int count = Integer.parseInt(sessionService.getTempData(chatId).getOrDefault("pt_count", "0"));
        return startTest(chatId, count, minutes);
    }

    public SendMessage applyCustomTimeLimit(Long chatId, String text) {
        int minutes;
        try {
            minutes = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Iltimos, faqat butun son kiriting (masalan: 15).");
            return msg;
        }

        if (minutes < 1 || minutes > MAX_TIME_LIMIT_MIN) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Vaqt 1 dan " + MAX_TIME_LIMIT_MIN + " daqiqagacha bo'lishi kerak. Qaytadan yozing.");
            return msg;
        }

        return applyTimeLimit(chatId, minutes);
    }

    private long parseAvailable(Long chatId) {
        String raw = sessionService.getTempData(chatId).get("pt_available");
        if (raw == null || raw.isBlank()) return 0;
        return Long.parseLong(raw);
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

    // Vaqt chegarasiz (Practice rejimi yoki to'g'ridan-to'g'ri chaqirilganda) —
    // testlarda va boshqa ichki chaqiruvlarda qulaylik uchun.
    public SendMessage startTest(Long chatId, int count) {
        return startTest(chatId, count, null);
    }

    public SendMessage startTest(Long chatId, int count, Integer timeLimitMinutes) {
        User user = getUserByChatId(chatId);
        List<Long> topicIds = parseIds(sessionService.getTempData(chatId).get("pt_topicIds"));
        String mode = currentMode(chatId);

        StartTestResponseDto response = testSessionService.startTest(user, topicIds, count, mode);

        List<PracticeTestState.QuestionSnapshot> snapshots = response.questions().stream()
                .map(this::toSnapshot)
                .toList();

        long startedAt = System.currentTimeMillis();
        Long deadline = timeLimitMinutes == null ? null : startedAt + timeLimitMinutes * 60_000L;

        PracticeTestState state = new PracticeTestState(
                response.testSessionId(),
                startedAt,
                0,
                snapshots,
                new ArrayList<>(),
                deadline
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
        sb.append("❓ Savol ").append(state.currentIndex() + 1).append("/").append(state.questions().size());
        if (state.deadlineEpochMilli() != null) {
            sb.append(" (⏱ qolgan vaqt: ").append(formatRemaining(state.deadlineEpochMilli())).append(")");
        }
        sb.append("\n\n").append(q.questionText()).append("\n\n");

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

        // Vaqt (Exam/Hard) bosilgan javob qayta ishlanishidan OLDIN allaqachon
        // tugagan bo'lsa — shu javobni hisobga olmasdan, mavjud javoblar
        // bilan darhol yakunlaymiz (saytdagi avtomatik yuborishga o'xshash).
        if (isExpired(state)) {
            return finishWithTimeoutNotice(chatId, state, state.answers());
        }

        PracticeTestState.QuestionSnapshot currentQuestion = state.questions().get(state.currentIndex());

        List<PracticeTestState.AnswerPick> answers = new ArrayList<>(state.answers());
        answers.add(new PracticeTestState.AnswerPick(currentQuestion.id(), answerId));

        int nextIndex = state.currentIndex() + 1;

        if (nextIndex >= state.questions().size()) {
            return finish(chatId, state, answers);
        }

        PracticeTestState next = new PracticeTestState(state.testSessionId(), state.startedAtEpochMilli(),
                nextIndex, state.questions(), answers, state.deadlineEpochMilli());
        saveState(chatId, next);

        return showQuestion(chatId, next);
    }

    private boolean isExpired(PracticeTestState state) {
        return state.deadlineEpochMilli() != null && System.currentTimeMillis() >= state.deadlineEpochMilli();
    }

    // ================= Jonli (real-time) qolgan vaqt sanog'i =================
    // Telegram xabari o'zi live-yangilanmaydi — shuning uchun
    // TelegramPracticeTestTimeoutService (@Scheduled) shu ma'lumot orqali
    // BITTA xabarni davriy ravishda tahrirlab (EditMessageText) turadi —
    // saytdagi jonli sekundomerga eng yaqin taqlid.

    public record TickInfo(long deadlineEpochMilli, Integer timerMessageId) {}

    public TickInfo getTickInfo(Long chatId) {
        PracticeTestState state = loadState(chatId);
        if (state == null || state.deadlineEpochMilli() == null || isExpired(state)) return null;

        String rawId = sessionService.getTempData(chatId).get("pt_timerMsgId");
        Integer timerMessageId = rawId == null || rawId.isBlank() ? null : Integer.parseInt(rawId);
        return new TickInfo(state.deadlineEpochMilli(), timerMessageId);
    }

    public void recordTimerMessageId(Long chatId, Integer messageId) {
        sessionService.putTempData(chatId, "pt_timerMsgId", String.valueOf(messageId));
    }

    // ================= Vaqt tugagach avtomatik yakunlash =================
    // TelegramPracticeTestTimeoutService (@Scheduled) tomonidan, foydalanuvchi
    // hech qanday tugma bosmasa ham, saytdagi jonli sekundomer 0'ga
    // yetganda avtomatik yuborilishi bilan bir xil natijani berish uchun
    // chaqiriladi.
    public SendMessage autoFinishIfExpired(Long chatId) {
        PracticeTestState state = loadState(chatId);
        if (state == null || !isExpired(state)) return null;

        return finishWithTimeoutNotice(chatId, state, state.answers());
    }

    private SendMessage finishWithTimeoutNotice(Long chatId, PracticeTestState state,
                                                  List<PracticeTestState.AnswerPick> answers) {
        // Davomiylik ANIQ belgilangan vaqt chegarasiga teng bo'lishi kerak —
        // haqiqiy tugallash vaqti emas (poller har necha soniyada bir marta
        // tekshiradi, shuning uchun bir necha soniya kech "sezilishi" mumkin;
        // agar buni hisobga olmasak, masalan 60 soniyalik testda "82 soniya"
        // kabi noto'g'ri (haqiqatdan uzunroq) davomiylik ko'rsatilardi).
        SendMessage msg = finish(chatId, state, answers, state.deadlineEpochMilli());
        msg.setText("⏰ Vaqt tugadi — test avtomatik yakunlandi.\n\n" + msg.getText());
        return msg;
    }

    private SendMessage finish(Long chatId, PracticeTestState state, List<PracticeTestState.AnswerPick> answers) {
        return finish(chatId, state, answers, System.currentTimeMillis());
    }

    private SendMessage finish(Long chatId, PracticeTestState state, List<PracticeTestState.AnswerPick> answers,
                                long finishedAt) {
        User user = getUserByChatId(chatId);
        String mode = currentMode(chatId); // clear()'dan OLDIN — keyin tempData yo'qoladi

        List<AnswerResultDto> results = answers.stream()
                .map(a -> new AnswerResultDto(a.questionId(), a.answerId()))
                .toList();

        testSessionService.finishTest(
                new FinishTestRequestDto(state.testSessionId(), state.startedAtEpochMilli(), finishedAt,
                        results, state.questions().size()),
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

        msg.setText("✅ <b>Test yakunlandi!</b> (" + modeLabel(mode) + ")\n\n" +
                "⭐ Natija: " + session.getPercent() + "%\n" +
                "✔ To'g'ri: " + session.getCorrectAnswers() + "/" + session.getTotalQuestions() + "\n" +
                "⏱ Davomiylik: " + session.getDurationSec() + " soniya");
        msg.setParseMode("HTML");
        return msg;
    }

    private String currentMode(Long chatId) {
        String mode = sessionService.getTempData(chatId).get("pt_mode");
        return mode == null || mode.isBlank() ? DEFAULT_MODE : mode;
    }

    // Telegram xabari statik (live-yangilanmaydi), shuning uchun bu —
    // xabar yuborilgan paytdagi taxminiy qolgan vaqt (saytdagi jonli
    // sekundomerning yaqin taxminiy o'rnini bosuvchisi). Public/static —
    // TelegramPracticeTestTimeoutService ham bir xil formatni ishlatishi uchun.
    public static String formatRemaining(long deadlineEpochMilli) {
        long remainingSec = Math.max(0, (deadlineEpochMilli - System.currentTimeMillis()) / 1000);
        long min = remainingSec / 60;
        long sec = remainingSec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private String modeLabel(String mode) {
        return switch (mode) {
            case "exam" -> "⏱ Exam";
            case "hard" -> "🔥 Hard";
            default -> "📝 Practice";
        };
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
