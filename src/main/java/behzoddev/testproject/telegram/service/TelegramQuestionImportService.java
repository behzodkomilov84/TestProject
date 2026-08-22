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
// (saytga kirmasdan): fan -> mavzu tanlab, .xlsx faylni botga yuborish
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
            msg.setText("🗂 Hozircha fanlar mavjud emas.");
            return msg;
        }

        msg.setText("🗂 Savol import qilmoqchi bo'lgan fanni tanlang:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ScienceIdAndNameDto science : sciences) {
            rows.add(List.of(button(science.name(), "tg_import_science_" + science.id())));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage selectScience(Long chatId, Long scienceId) {
        List<TopicWithQuestionCountDto> topics = topicService.getTopicsWithQuestionCount(scienceId);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (topics.isEmpty()) {
            User user = getUserByChatId(chatId);
            String url = autoLoginService.buildLoginUrl(user, "/science");
            msg.setText("🗂 Bu fanda hozircha mavzu yo'q. Avval saytda mavzu yarating: " + url);
            msg.setDisableWebPagePreview(true);
            return msg;
        }

        msg.setText("🗂 Mavzuni tanlang:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (TopicWithQuestionCountDto topic : topics) {
            rows.add(List.of(button(topic.name() + " (" + topic.questionCount() + " ta savol)",
                    "tg_import_topic_" + topic.id())));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
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
            msg.setText("⚠️ Mavzu tanlanmagan. 🗂 Savollar boshqaruvi bo'limidan qaytadan boshlang.");
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
        msg.setText("🗂 Fayl yuborishdan oldin avval fan/mavzuni tanlang (🗂 Savollar boshqaruvi).");
        return msg;
    }

    // Mavzu tanlangan, lekin foydalanuvchi fayl o'rniga oddiy matn yozganda.
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
}
