package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.TelegramLinkCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.student.ResponseAssignmentsAndTaskStatusDto;
import behzoddev.testproject.dto.testsession.TestSessionHistoryDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.TelegramLinkCode;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.TaskStatus;
import behzoddev.testproject.service.AssignmentAttemptService;
import behzoddev.testproject.service.SubscriptionService;
import behzoddev.testproject.service.TestSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final TestSessionService testSessionService;
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    // Akkaunt Telegramga ulanganmi — bo'lmasa null (chaqiruvchi tomon
    // "avval ulang" xabarini ko'rsatadi). Ulangan bo'lsa, User qaytariladi —
    // rolga qarab menyu qurish TelegramMenuService'ga tegishli.
    public User resolveLinkedUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).orElse(null);
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

    // Natijalarim — o'qituvchi bergan topshiriqlar (AssignmentAttempt) VA
    // mustaqil (bot yoki saytdagi) testlar (TestSession) birlashtirilib,
    // eng oxirgi 10 tasi (sanaga qarab) ko'rsatiladi. Ilgari faqat
    // topshiriqlar ko'rsatilardi — botda mustaqil test yechgan
    // foydalanuvchi "hali test topshirmagansiz" degan chalkash xabar
    // olardi (haqiqatda TestSession'ga saqlanган edi, shu joyda
    // ko'rinmayotgan edi).
    private static final int MY_RESULTS_LIMIT = 10;

    private record ResultItem(String label, int percent, LocalDateTime finishedAt) {}

    public SendMessage sendMyResults(Long chatId) {

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        User user = userRepository.findByTelegramId(chatId).orElse(null);

        List<ResultItem> items = new ArrayList<>();

        for (AssignmentAttempt a : assignmentAttemptRepository.findByPupil_TelegramId(chatId)) {
            if (a.getFinishedAt() == null) continue;
            items.add(new ResultItem(
                    "📌 " + a.getAssignment().getQuestionSet().getName(),
                    a.getPercent(),
                    a.getFinishedAt()));
        }

        if (user != null) {
            Pageable pageable = PageRequest.of(0, MY_RESULTS_LIMIT, Sort.by("finishedAt").descending());
            for (TestSessionHistoryDto t : testSessionService.getHistory(user, pageable).getContent()) {
                items.add(new ResultItem(
                        "🎯 " + t.scienceName() + " (mustaqil test)",
                        t.percent(),
                        t.finishedAt()));
            }
        }

        if (items.isEmpty()) {
            msg.setText("Siz hali test topshirmagansiz.");
            return msg;
        }

        items.sort(Comparator.comparing(ResultItem::finishedAt).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Sizning oxirgi ").append(Math.min(items.size(), MY_RESULTS_LIMIT)).append(" ta natijangiz:\n\n");

        items.stream().limit(MY_RESULTS_LIMIT).forEach(item -> sb
                .append(item.label()).append("\n")
                .append("⭐ Ball: ").append(item.percent()).append("%")
                .append("  •  ").append(item.finishedAt().format(DATE_TIME_FORMATTER))
                .append("\n\n"));

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