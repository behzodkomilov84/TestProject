package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.service.ExcelService;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TopicService;
import behzoddev.testproject.telegram.state.BotState;
import behzoddev.testproject.telegram.util.ByteArrayMultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

// Botda "🗂 Savollar boshqaruvi" — hozircha faqat Excel fayl orqali import
// (saytga kirmasdan): fan -> mavzu tanlab, .xlsx faylni botga yuborish
// yetarli. Haqiqiy ExcelService orqali — saytdagi bilan bir xil
// validatsiya (magic-byte, ClamAV, qator-qator xatolik izolyatsiyasi).
@Service
@RequiredArgsConstructor
public class TelegramQuestionImportService {

    private final ScienceService scienceService;
    private final TopicService topicService;
    private final ExcelService excelService;
    private final TelegramSessionService sessionService;

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
            msg.setText("🗂 Bu fanda hozircha mavzu yo'q. Avval saytda (/science) mavzu yarating.");
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
                "Shablon kerak bo'lsa, saytdagi \"Test yaratish\" sahifasidan (\"📄 Shablon\" tugmasi) yuklab oling.\n\n" +
                "(Bekor qilish uchun /cancel)");
        return msg;
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

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}
