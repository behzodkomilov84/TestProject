package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ExcelService;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TopicService;
import behzoddev.testproject.telegram.state.BotState;
import behzoddev.testproject.telegram.util.ByteArrayMultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Botda "🗂 Savollar boshqaruvi" — hozircha faqat Excel fayl orqali import
// (saytga kirmasdan): bo'lim -> dars tanlab, .xlsx faylni botga yuborish
// yetarli. Haqiqiy ExcelService orqali — saytdagi bilan bir xil
// validatsiya (magic-byte, ClamAV, qator-qator xatolik izolyatsiyasi).
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramQuestionImportService {

    // Saytdagi "Test yaratish" sahifasidagi "📄 Shablon" tugmasi bilan
    // bir xil fayl (ExcelImportController.downloadTemplate) — jar ichida
    // paketlangan classpath resursi.
    private static final String TEMPLATE_CLASSPATH = "templates/template_For_Import.xlsx";
    private static final String TEMPLATE_FILE_NAME = "template_For_Import.xlsx";

    // Darslar ro'yxati sahifalanadi (masalan Kimyo'da 45 ta dars bor —
    // bittasi bitta xabarda hammasi chiqsa, juda uzun/noqulay ro'yxat
    // bo'lib qolardi). TelegramCourseReaderService.SECTIONS_PER_PAGE bilan
    // bir xil konvensiya.
    private static final int TOPICS_PER_PAGE = 8;
    // Bo'lim/Mavzu/Dars ro'yxatlari RAQAMLI, gorizontal katakcha ko'rinishida
    // (TelegramPracticeTestService/TelegramCourseReaderService bilan bir xil g'oya).
    private static final int BUTTONS_PER_ROW = 4;
    // Ro'yxat bandlari orasidagi ajratuvchi chiziq — nomlar uzun bo'lganda
    // (ayniqsa ko'p qatorli) bandlar bir-biriga "yopishib" ketmasligi uchun
    // (foydalanuvchi so'rovi bo'yicha).
    private static final String LIST_SEPARATOR = "➖➖➖➖➖➖➖➖➖➖\n";

    private final ScienceService scienceService;
    private final TopicService topicService;
    private final ExcelService excelService;
    private final TelegramSessionService sessionService;
    private final UserRepository userRepository;
    private final TelegramAutoLoginService autoLoginService;

    public SendMessage startFlow(Long chatId) {
        List<ScienceIdAndNameDto> sciences = scienceService.getSciences();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (sciences.isEmpty()) {
            msg.setText("🗂 Hozircha bo'limlar mavjud emas.");
            return msg;
        }

        // To'liq bo'lim nomlari matnda raqamlangan ro'yxat sifatida, tugmalar
        // faqat mos raqam bilan (gorizontal katakcha).
        StringBuilder sb = new StringBuilder("🗂 Savol import qilmoqchi bo'lgan bo'limni tanlang:\n\n");
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        int i = 1;
        for (ScienceIdAndNameDto science : sciences) {
            if (i > 1) sb.append(LIST_SEPARATOR);
            sb.append(i).append(". ").append(science.name()).append("\n");
            buttons.add(button(String.valueOf(i), "tg_import_science_" + science.id()));
            i++;
        }

        msg.setText(sb.toString());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(chunkIntoRows(buttons));
        msg.setReplyMarkup(markup);
        return msg;
    }

    // Bo'lim tanlangach — agar shu bo'limda Mavzu(lar) bo'lsa (masalan Kimyo),
    // avval Mavzu tanlanadi (TelegramPracticeTestService bilan bir xil
    // mantiq); Mavzusi yo'q bo'limlarda bu qadam o'tkazib yuboriladi va
    // to'g'ridan-to'g'ri (eskicha) dars ro'yxatiga o'tiladi.
    public SendMessage selectScience(Long chatId, Long scienceId) {
        List<TopicWithQuestionCountDto> topics = topicService.getTopicsWithQuestionCount(scienceId);

        if (topics.isEmpty()) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            User user = getUserByChatId(chatId);
            String url = autoLoginService.buildLoginUrl(user, "/science");
            msg.setText("🗂 Bu bo'limda hozircha dars yo'q. Avval saytda dars yarating: " + url);
            msg.setDisableWebPagePreview(true);
            return msg;
        }

        boolean hasSections = topics.stream().anyMatch(t -> t.sectionId() != null);
        if (!hasSections) {
            return selectSciencePage(chatId, scienceId, "all", 0);
        }

        return showSectionSelection(chatId, scienceId, topics);
    }

    private SendMessage showSectionSelection(Long chatId, Long scienceId, List<TopicWithQuestionCountDto> topics) {
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

        StringBuilder sb = new StringBuilder("🗂 Mavzuni tanlang:\n\n");
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        int i = 1;
        for (SectionInfo sec : sorted) {
            if (i > 1) sb.append(LIST_SEPARATOR);
            sb.append(i).append(". ").append(sec.name()).append("\n");
            buttons.add(button(String.valueOf(i), "tg_import_section_" + scienceId + "_" + sec.id()));
            i++;
        }
        if (hasUnassigned) {
            if (i > 1) sb.append(LIST_SEPARATOR);
            sb.append(i).append(". — Mavzusiz darslar —\n");
            buttons.add(button(String.valueOf(i), "tg_import_section_" + scienceId + "_none"));
            i++;
        }
        if (i > 1) sb.append(LIST_SEPARATOR);
        sb.append(i).append(". 🔷 Barchasi (shu bo'lim bo'yicha)\n");
        buttons.add(button(String.valueOf(i), "tg_import_section_" + scienceId + "_all"));

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>(chunkIntoRows(buttons));
        rows.add(List.of(button("🔙 Orqaga", "tg_import_back_science")));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    // Mavzu tanlangach — sectionValue "all"/"none"/haqiqiy mavzu id'si
    // (TelegramPracticeTestService.selectSection bilan bir xil qoida).
    public SendMessage selectSection(Long chatId, Long scienceId, String sectionValue) {
        return selectSciencePage(chatId, scienceId, sectionValue, 0);
    }

    // "◀️/▶️" navigatsiya tugmalari shu orqali chaqiriladi (tg_import_topicspage_).
    public SendMessage selectSciencePage(Long chatId, Long scienceId, String sectionValue, int page) {
        List<TopicWithQuestionCountDto> allTopics = topicService.getTopicsWithQuestionCount(scienceId);
        boolean hasSections = allTopics.stream().anyMatch(t -> t.sectionId() != null);
        List<TopicWithQuestionCountDto> topics = filterBySection(allTopics, sectionValue);
        String backCallback = hasSections ? "tg_import_back_section_" + scienceId : "tg_import_back_science";

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (topics.isEmpty()) {
            msg.setText("🗂 Bu mavzuda hozircha dars yo'q.");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(button("🔙 Orqaga", backCallback))));
            msg.setReplyMarkup(markup);
            return msg;
        }

        int totalPages = (int) Math.ceil(topics.size() / (double) TOPICS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * TOPICS_PER_PAGE;
        int to = Math.min(from + TOPICS_PER_PAGE, topics.size());

        // Dars nomlari matnda raqamlangan ro'yxat sifatida (sahifalar
        // bo'ylab ketma-ket raqamlanadi), tugmalar faqat mos raqam bilan
        // (gorizontal katakcha).
        StringBuilder sb = new StringBuilder(totalPages > 1
                ? "🗂 Darsni tanlang (" + (safePage + 1) + "/" + totalPages + "-sahifa):\n\n"
                : "🗂 Darsni tanlang:\n\n");

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        int i = from + 1;
        for (TopicWithQuestionCountDto topic : topics.subList(from, to)) {
            if (i > from + 1) sb.append(LIST_SEPARATOR);
            sb.append(i).append(". ").append(topic.name()).append(" (").append(topic.questionCount()).append(" ta savol)\n");
            buttons.add(button(String.valueOf(i), "tg_import_topic_" + topic.id()));
            i++;
        }

        msg.setText(sb.toString());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>(chunkIntoRows(buttons));

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        String pageCallbackBase = "tg_import_topicspage_" + scienceId + "_" + sectionValue + "_";
        if (safePage > 0) {
            navRow.add(button("◀️", pageCallbackBase + (safePage - 1)));
        }
        if (safePage < totalPages - 1) {
            navRow.add(button("▶️", pageCallbackBase + (safePage + 1)));
        }
        if (!navRow.isEmpty()) rows.add(navRow);

        rows.add(List.of(button("🔙 Orqaga", backCallback)));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    private List<TopicWithQuestionCountDto> filterBySection(List<TopicWithQuestionCountDto> allTopics, String sectionValue) {
        if ("all".equals(sectionValue)) return allTopics;
        if ("none".equals(sectionValue)) return allTopics.stream().filter(t -> t.sectionId() == null).toList();
        Long targetSectionId = Long.valueOf(sectionValue);
        return allTopics.stream().filter(t -> targetSectionId.equals(t.sectionId())).toList();
    }

    public SendMessage selectTopic(Long chatId, Long topicId) {
        sessionService.putTempData(chatId, "tg_importTopicId", topicId.toString());
        sessionService.setState(chatId, BotState.AWAITING_EXCEL_FILE);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("📄 Endi .xlsx faylni shu chatga yuboring.\n\n" +
                "Shablon kerak bo'lsa, pastdagi tugma orqali shu yerning o'zida yuklab oling.\n\n" +
                "(Bekor qilish uchun /cancel)");

        InlineKeyboardButton templateBtn = button("📄 Shablonni yuklab olish", "tg_import_template");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(templateBtn)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    // "📄 Shablonni yuklab olish" tugmasi — saytga o'tishga hojatsiz,
    // shablon fayli to'g'ridan-to'g'ri botning o'zida hujjat sifatida
    // yuboriladi (kurs kitobini yuklab olish bilan bir xil g'oya —
    // TelegramCourseReaderService.buildDocument). E'tibor bering: fayl
    // baytlari OLDINDAN o'qib olinadi (ByteArrayInputStream'ga) — jar
    // ichidagi classpath resursini File sifatida ololmaymiz, InputStream
    // esa Telegram kutubxonasi so'rovni haqiqatan yuborayotganda (bu
    // metod tugagandan KEYIN) o'qiydi, shuning uchun try-with-resources
    // bilan darhol yopib bo'lmaydi.
    public SendDocument sendTemplate(Long chatId) {
        SendDocument doc = new SendDocument();
        doc.setChatId(chatId.toString());

        try {
            byte[] bytes = new ClassPathResource(TEMPLATE_CLASSPATH).getInputStream().readAllBytes();
            doc.setDocument(new InputFile(new ByteArrayInputStream(bytes), TEMPLATE_FILE_NAME));
        } catch (IOException e) {
            log.error("Excel shablon faylini o'qishda xatolik", e);
        }

        return doc;
    }

    public SendMessage importFile(Long chatId, byte[] fileBytes, String fileName) {
        String topicIdStr = sessionService.getTempData(chatId).get("tg_importTopicId");
        sessionService.clear(chatId);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (topicIdStr == null) {
            msg.setText("⚠️ Dars tanlanmagan. 🗂 Savollar boshqaruvi bo'limidan qaytadan boshlang.");
            return msg;
        }

        Long topicId = Long.valueOf(topicIdStr);
        ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(
                "file", fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);

        ImportResultDto result = excelService.importQuestions(multipartFile, topicId);

        if (result.success()) {
            msg.setText("✅ Import muvaffaqiyatli! " + result.imported() + " ta savol qo'shildi.");
        } else {
            StringBuilder sb = new StringBuilder("⚠️ Import qisman bajarildi (" + result.imported() + " ta qo'shildi).\n\nXatolar:\n");
            for (String err : result.errors()) {
                sb.append("• ").append(err).append("\n");
            }
            msg.setText(sb.toString());
        }
        return msg;
    }

    public SendMessage notWaitingForFile(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("🗂 Fayl yuborishdan oldin avval bo'lim/darsni tanlang (🗂 Savollar boshqaruvi).");
        return msg;
    }

    // Dars tanlangan, lekin foydalanuvchi fayl o'rniga oddiy matn yozganda.
    public SendMessage remindToSendFile(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("📄 Iltimos, .xlsx faylning o'zini (hujjat sifatida) yuboring, yoki /cancel yozing.");
        return msg;
    }

    private User getUserByChatId(Long chatId) {
        return userRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    // Tekis tugmalar ro'yxatini BUTTONS_PER_ROW tadan qatorlarga bo'ladi
    // (TelegramCourseReaderService/TelegramPracticeTestService bilan bir xil g'oya).
    private List<List<InlineKeyboardButton>> chunkIntoRows(List<InlineKeyboardButton> buttons) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += BUTTONS_PER_ROW) {
            rows.add(new ArrayList<>(buttons.subList(i, Math.min(i + BUTTONS_PER_ROW, buttons.size()))));
        }
        return rows;
    }
}
