package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.teacher.AssignmentAdminRowDto;
import behzoddev.testproject.dto.teacher.ChatMessageDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentService;
import behzoddev.testproject.service.TeacherService;
import behzoddev.testproject.telegram.state.BotState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Botda "💬 Topshiriq chatlari" — har bir topshiriq (Assignment) uchun
// bitta umumiy chat (o'qituvchi + shu guruhdagi barcha o'quvchilar),
// saytdagi /admin-assignment sahifasidagi chat bilan bir xil
// (AssignmentService.getChatForUser/sendMessage).
@Service
@RequiredArgsConstructor
public class TelegramAssignmentChatService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final int MAX_MESSAGES_SHOWN = 15;

    private final TeacherService teacherService;
    private final AssignmentService assignmentService;
    private final UserRepository userRepository;
    private final TelegramSessionService sessionService;

    public SendMessage listAssignments(User teacher) {
        List<AssignmentAdminRowDto> assignments = teacherService.getAllAssignments(teacher);

        SendMessage msg = new SendMessage();
        msg.setChatId(teacher.getTelegramId().toString());

        if (assignments.isEmpty()) {
            msg.setText("💬 Hozircha topshiriqlar yo'q.");
            return msg;
        }

        msg.setText("💬 Chatni ko'rish uchun topshiriqni tanlang:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (AssignmentAdminRowDto a : assignments) {
            rows.add(List.of(button(a.questionSetName() + " — " + a.groupName(), "tg_chat_" + a.id())));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage showChat(Long chatId, Long assignmentId) {
        User user = getUserByChatId(chatId);
        List<ChatMessageDto> messages = assignmentService.getChatForUser(assignmentId, user);

        StringBuilder sb = new StringBuilder("💬 <b>Chat</b>\n\n");
        if (messages.isEmpty()) {
            sb.append("Hozircha xabar yo'q.\n");
        } else {
            List<ChatMessageDto> shown = messages.size() > MAX_MESSAGES_SHOWN
                    ? messages.subList(messages.size() - MAX_MESSAGES_SHOWN, messages.size())
                    : messages;
            for (ChatMessageDto m : shown) {
                sb.append("<b>").append(escape(m.senderName())).append("</b> (")
                        .append(m.createdAt().format(TIME_FORMAT)).append("):\n")
                        .append(escape(m.message())).append("\n\n");
            }
        }
        sb.append("✏️ Javob yozish uchun shu yerga matn yuboring (yoki /cancel).");

        sessionService.setState(chatId, BotState.AWAITING_CHAT_MESSAGE);
        sessionService.putTempData(chatId, "tg_chatAssignmentId", assignmentId.toString());

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());
        msg.setParseMode("HTML");
        return msg;
    }

    public SendMessage sendReply(Long chatId, String text) {
        Long assignmentId = Long.valueOf(sessionService.getTempData(chatId).get("tg_chatAssignmentId"));
        User user = getUserByChatId(chatId);

        assignmentService.sendMessage(assignmentId, user.getId(), text);

        // Xabar yuborilgach, yangilangan chatni qayta ko'rsatamiz (holat davom etadi —
        // foydalanuvchi bir nechta xabarni ketma-ket yozishi mumkin).
        return showChat(chatId, assignmentId);
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private User getUserByChatId(Long chatId) {
        return userRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
