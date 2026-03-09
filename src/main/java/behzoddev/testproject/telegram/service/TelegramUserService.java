package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.TelegramLinkCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.TelegramLinkCode;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TelegramUserService {

    private final UserRepository userRepository;
    private final TelegramLinkCodeRepository telegramLinkCodeRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentAttemptRepository assignmentAttemptRepository;

    public SendMessage handleStart(Message message) {

        Long telegramId = message.getFrom().getId();

        SendMessage msg = new SendMessage();
        msg.setChatId(message.getChatId().toString());

        User user = userRepository
                .findByTelegramId(telegramId)
                .orElse(null);

        if (user == null) {
            msg.setText("Avval sayt orqali Telegramni ulang.");
            return msg;
        }

        msg.setText("Student paneliga xush kelibsiz 👋");
        msg.setReplyMarkup(studentKeyboard());

        return msg;
    }

    public ReplyKeyboardMarkup studentKeyboard() {

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📚 Mening topshiriqlarim");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📊 Natijalarim");

        keyboard.setKeyboard(List.of(row1, row2));

        return keyboard;
    }

    public SendMessage handleMessage(Message msg) {

        String text = msg.getText();

        if (text.startsWith("/link ")) {

            String code = text.substring(6).trim();

            return linkTelegram(msg, code);
        }

        SendMessage response = new SendMessage();
        response.setChatId(msg.getChatId().toString());
        response.setText("Noma'lum buyruq!!! Kodni ushbu tartibda kiriting: /link 123456");
        return response;
    }


    public SendMessage linkTelegram(Message msg, String code) {

        TelegramLinkCode link =
                telegramLinkCodeRepository.findByCodeAndUsedFalse(code)
                        .orElseThrow(() ->
                                new RuntimeException("Kod topilmadi"));

        if (link.getCreatedAt()
                .isBefore(LocalDateTime.now().minusMinutes(5)))
            throw new RuntimeException("Kod eskirgan");

        User user = link.getUser();

        user.setTelegramId(msg.getFrom().getId());

        userRepository.save(user);

        link.setUsed(true);
        telegramLinkCodeRepository.save(link);

        SendMessage response = new SendMessage();

        response.setChatId(msg.getChatId().toString());
        response.setText("Bot muvaffaqiyatli ulandi ✅");

        return response;
    }


    public SendMessage sendMyAssignments(Long chatId) {

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        List<Assignment> assignments =
                assignmentRepository.findByRecipientTelegramId(chatId);

        if (assignments.isEmpty()) {
            msg.setText("Sizda hali topshiriq yo'q 📭");
            return msg;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📚 Sizning topshiriqlaringiz:\n\n");

        for (Assignment a : assignments) {

            sb.append("📌 Savol paketi: ")
                    .append(a.getQuestionSet().getName())
                    .append("\n");

            sb.append("👥 Guruh: ")
                    .append(a.getGroup().getName())
                    .append("\n");

            sb.append("⏳ Muddat: ")
                    .append(a.getDueDate())
                    .append("\n\n");
        }

        msg.setText(sb.toString());

        return msg;
    }

    public SendMessage sendMyResults(Long chatId) {

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        List<AssignmentAttempt> attempts =
                assignmentAttemptRepository.findByPupil_TelegramId(chatId);

        if (attempts.isEmpty()) {
            msg.setText("Siz hali test topshirmagansiz.");
            return msg;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Sizning natijalaringiz:\n\n");

        for (AssignmentAttempt a : attempts) {

            sb.append("📌 ")
                    .append(a.getAssignment().getQuestionSet().getName())
                    .append("\n");

            sb.append("⭐ Ball: ")
                    .append(a.getPercent())
                    .append("\n\n");
        }

        msg.setText(sb.toString());

        return msg;
    }
}