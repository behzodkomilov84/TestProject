package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.TelegramLinkCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.student.ResponseAssignmentsAndTaskStatusDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.TelegramLinkCode;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.TaskStatus;
import behzoddev.testproject.service.AssignmentAttemptService;
import behzoddev.testproject.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TelegramUserService {

    private final UserRepository userRepository;
    private final TelegramLinkCodeRepository telegramLinkCodeRepository;
    private final AssignmentAttemptRepository assignmentAttemptRepository;
    private final AssignmentAttemptService assignmentAttemptService;
    private final AssignmentRepository assignmentRepository;
    private final SubscriptionService subscriptionService;
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

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
        row1.add("📊 Natijalarim");

        keyboard.setKeyboard(List.of(row1));

        return keyboard;
    }

    public SendMessage handleMessage(Message msg) {

        String text = msg.getText();

        if (text.startsWith("/link ")) {

            String code = text.substring(6).trim();

            return linkTelegram(msg, code);
        }

        if (text.startsWith("/pay ")) {

            String amountText = text.substring(5).trim();

            return requestAdminSubscription(msg, amountText);
        }

        SendMessage response = new SendMessage();
        response.setChatId(msg.getChatId().toString());
        response.setText("Noma'lum buyruq!!! Kodni ushbu tartibda kiriting: /link 123456\n\n" +
                "ADMIN (o'qituvchi) huquqiga o'tish uchun to'lovni tasdiqlashga so'rov yuborish: /pay 50000\n" +
                "(to'lov chekini/skrinshotini shu botga alohida xabar sifatida yuboring — OWNER ko'rib chiqib tasdiqlaydi)");
        return response;
    }

    // Foydalanuvchi Telegram orqali "/pay <summa>" yuborganda ADMIN obunasiga
    // PENDING so'rov yaratiladi. Haqiqiy to'lov tasdiqlanishi (chek/skrinshot)
    // hozircha botda avtomatlashtirilmagan — OWNER buni /users sahifasida
    // ko'rib chiqib qo'lda tasdiqlaydi yoki rad etadi.
    public SendMessage requestAdminSubscription(Message msg, String amountText) {

        SendMessage response = new SendMessage();
        response.setChatId(msg.getChatId().toString());

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.replace(",", "."));
        } catch (NumberFormatException e) {
            response.setText("❌ Summa noto'g'ri. Masalan: /pay 50000");
            return response;
        }

        try {
            subscriptionService.createPendingFromTelegram(msg.getFrom().getId(), amount);
            response.setText("✅ So'rovingiz qabul qilindi (" + amountText + " so'm).\n" +
                    "OWNER tasdiqlagach, ADMIN (o'qituvchi) huquqi ochiladi.");
        } catch (IllegalArgumentException e) {
            response.setText("❌ " + e.getMessage());
        }

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

        User pupil = userRepository
                .findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));

        List<ResponseAssignmentsAndTaskStatusDto> tasks =
                assignmentAttemptService.getTasksAndTaskStatus(pupil);

        if (tasks.isEmpty()) {
            msg.setText("Sizda hali topshiriq yo'q 📭");
            return msg;
        }

        String text =
                "📚 Topshiriqlar\n\n" +
                        "\t ℹ️ Belgilar:\n\n" +
                        "\t 🆕 — yangi\n" +
                        "\t ⏳ — davom etmoqda\n" +
                        "\t ✅ — tugatilgan\n" +
                        "\t ❌ — muddat o'tgan";

        msg.setText(text);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (ResponseAssignmentsAndTaskStatusDto task : tasks) {
            StringBuilder sb = new StringBuilder();

            InlineKeyboardButton button = new InlineKeyboardButton();



            String deadlineText = formatDeadline(task.dueDate(), task.taskStatus());


            sb.append("📌 ")
                    .append(task.questionSetName())
                    .append("\t");

            sb.append(getStatusEmoji(task.taskStatus()))
                    .append("\t");

            sb.append(deadlineText);

            button.setText("▶ " + sb);

            button.setCallbackData("assignment_" + task.id());

            rows.add(List.of(button));
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        msg.setReplyMarkup(markup);

        return msg;
    }

    private String formatDeadline(LocalDateTime dueDate, TaskStatus status) {

        if (dueDate == null) return "";

        if (status == TaskStatus.FINISHED) {
            return "";
        }

        LocalDateTime now = LocalDateTime.now();

        long days = java.time.Duration
                .between(now, dueDate)
                .toDays();

        if (status == TaskStatus.OVERDUE) {
            return "(Muddat o'tgan)";
        }

        if (days == 0) {
            return "(Bugun)";
        }

        if (days == 1) {
            return "(1 kun qoldi)";
        }

        return "(" + days + " kun qoldi)";
    }

    private String getStatusEmoji(TaskStatus status) {

        return switch (status) {
            case NEW -> "🆕";
            case IN_PROGRESS -> "⏳";
            case FINISHED -> "✅";
            case OVERDUE -> "❌";
        };
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

    public SendMessage showAssignmentInfo(Long chatId, Long assignmentId) {

        Assignment assignment =
                assignmentRepository.findById(assignmentId)
                        .orElseThrow(() -> new RuntimeException("Topshiriq topilmadi"));

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        String text =
                "📘 " + assignment.getQuestionSet().getName() + "\n\n" +
                        "👨‍🏫 O'qituvchi: " + assignment.getAssignedBy().getUsername() + "\n" +
                        "👥 Guruh: " + assignment.getGroup().getName() + "\n" +
                        "❓ Savollar: " + assignment.getQuestionSet().getQuestions().size() + " ta\n" +
                        "⏳ Muddat: " + assignment.getDueDate().format(DATE_TIME_FORMATTER);

        msg.setText(text);

        InlineKeyboardButton startButton = new InlineKeyboardButton();
        startButton.setText("▶ Testni boshlash");
        startButton.setCallbackData("start_test_" + assignmentId);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(startButton)));

        msg.setReplyMarkup(markup);

        return msg;
    }
}