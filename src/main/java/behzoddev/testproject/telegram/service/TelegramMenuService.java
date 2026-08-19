package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.notification.NotificationDto;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseService;
import behzoddev.testproject.service.NotificationService;
import behzoddev.testproject.service.SubscriptionService;
import behzoddev.testproject.service.payment.ClickService;
import behzoddev.testproject.service.payment.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Botning asosiy (rolga qarab) menyusi va "umumiy" (barcha rollarga tegishli)
// bo'limlar — Bildirishnomalar, Obunam, Kurslar, Yordam. Rolga xos bo'limlar
// (ADMIN/OWNER) alohida servislarda — bular hali ROADMAP'dagi keyingi
// bosqichlarda qo'shiladi, hozircha tugmalar "tez orada" deb javob beradi.
@Service
@RequiredArgsConstructor
public class TelegramMenuService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static final String BTN_PROFILE = "👤 Profil";
    public static final String BTN_NOTIFICATIONS = "🔔 Bildirishnomalar";
    public static final String BTN_SUBSCRIPTION = "💳 Obunam";
    public static final String BTN_COURSES = "📚 Kurslar";
    public static final String BTN_HELP = "ℹ️ Yordam";
    public static final String BTN_MY_ASSIGNMENTS = "📚 Mening topshiriqlarim";
    public static final String BTN_MY_RESULTS = "📊 Natijalarim";
    public static final String BTN_MY_GROUPS = "👥 Gruppalarim";
    public static final String BTN_NEW_ASSIGNMENT = "📝 Topshiriq berish";
    public static final String BTN_QUESTIONS = "🗂 Savollar boshqaruvi";
    public static final String BTN_USERS = "👑 Foydalanuvchilar";
    public static final String BTN_PAYMENTS = "💰 To'lovlar";
    public static final String BTN_SETTINGS = "⚙️ Tizim sozlamalari";
    public static final String BTN_BROADCAST = "📢 E'lon yuborish";

    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;
    private final CourseService courseService;
    private final PaymentOrderService paymentOrderService;
    private final ClickService clickService;

    // ================= Asosiy menyu (ReplyKeyboardMarkup) =================

    public ReplyKeyboardMarkup buildMainMenu(User user) {
        List<KeyboardRow> rows = new ArrayList<>();

        boolean isOwner = user.hasRole("ROLE_OWNER");
        boolean isAdmin = user.hasRole("ROLE_ADMIN");
        boolean isStudent = user.hasRole("ROLE_USER");

        if (isStudent) {
            rows.add(row(BTN_MY_ASSIGNMENTS, BTN_MY_RESULTS));
        }

        if (isAdmin || isOwner) {
            rows.add(row(BTN_MY_GROUPS, BTN_NEW_ASSIGNMENT));
            rows.add(row(BTN_QUESTIONS));
        }

        if (isOwner) {
            rows.add(row(BTN_USERS, BTN_PAYMENTS));
            rows.add(row(BTN_SETTINGS, BTN_BROADCAST));
        }

        rows.add(row(BTN_COURSES, BTN_NOTIFICATIONS));

        // OWNER'ga ADMIN obunasi kerak emas (saytdagi bilan bir xil mantiq).
        if (isOwner) {
            rows.add(row(BTN_PROFILE, BTN_HELP));
        } else {
            rows.add(row(BTN_PROFILE, BTN_SUBSCRIPTION));
            rows.add(row(BTN_HELP));
        }

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private KeyboardRow row(String... buttons) {
        KeyboardRow row = new KeyboardRow();
        row.addAll(List.of(buttons));
        return row;
    }

    public String welcomeText(User user) {
        String roleLabel;
        if (user.hasRole("ROLE_OWNER")) roleLabel = "OWNER";
        else if (user.hasRole("ROLE_ADMIN")) roleLabel = "O'qituvchi (ADMIN)";
        else roleLabel = "O'quvchi";

        return "👋 Xush kelibsiz, " + user.getUsername() + "!\n" +
                "Rolingiz: " + roleLabel + "\n\n" +
                "Quyidagi menyudan foydalaning, yoki ℹ️ Yordam bo'limidan buyruqlar ro'yxatini ko'ring.";
    }

    // Hali qo'shilmagan (ROADMAP'dagi keyingi bosqich) bo'limlar uchun.
    public SendMessage comingSoon(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("🚧 Bu bo'lim tez orada qo'shiladi. Hozircha saytdan (study-grow.uz) foydalaning.");
        return msg;
    }

    // ================= Yordam =================

    public SendMessage help(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("ℹ️ <b>Yordam</b>\n\n");
        sb.append("👤 Profil — ma'lumotlaringizni ko'rish/tahrirlash.\n");
        sb.append("🔔 Bildirishnomalar — o'qilmagan xabarlar.\n");
        sb.append("📚 Kurslar — mavjud kurslar ro'yxati.\n");

        if (user.hasRole("ROLE_USER")) {
            sb.append("📚 Mening topshiriqlarim — sizga berilgan testlar.\n");
            sb.append("📊 Natijalarim — o'tgan testlaringiz natijalari.\n");
            sb.append("💳 Obunam — ADMIN huquqini onlayn sotib olish.\n");
        }

        if (!user.hasRole("ROLE_OWNER")) {
            sb.append("\n/pay &lt;summa&gt; — to'lov chekini qo'lda tasdiqlatish uchun so'rov (masalan: /pay 50000).\n");
        }

        sb.append("/cancel — joriy amalni bekor qilish.\n");
        sb.append("/menu — asosiy menyuni qayta ko'rsatish.");

        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());
        msg.setText(sb.toString());
        msg.setParseMode("HTML");
        return msg;
    }

    // ================= Bildirishnomalar =================

    public SendMessage showNotifications(User user) {
        List<NotificationDto> all = notificationService.list(user);
        List<NotificationDto> unread = all.stream().filter(n -> !n.read()).limit(10).toList();

        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());

        if (all.isEmpty()) {
            msg.setText("🔔 Sizda hali bildirishnoma yo'q.");
            return msg;
        }

        if (unread.isEmpty()) {
            msg.setText("🔔 Barcha bildirishnomalar o'qilgan ✅\n\nJami: " + all.size() + " ta.");
            return msg;
        }

        StringBuilder sb = new StringBuilder("🔔 <b>O'qilmagan bildirishnomalar</b> (" + unread.size() + ")\n\n");
        List<InlineKeyboardButton> buttons = new ArrayList<>();

        for (NotificationDto n : unread) {
            sb.append("• ").append(escape(n.message()))
                    .append("\n  ").append(n.createdAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")))
                    .append("\n\n");
        }

        InlineKeyboardButton markAll = new InlineKeyboardButton();
        markAll.setText("✅ Barchasini o'qilgan deb belgilash");
        markAll.setCallbackData("notif_read_all");
        buttons.add(markAll);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(buttons));

        msg.setText(sb.toString());
        msg.setParseMode("HTML");
        msg.setReplyMarkup(markup);
        return msg;
    }

    public void markAllNotificationsRead(User user) {
        notificationService.markAllRead(user);
    }

    // ================= Obunam (ADMIN obunasi holati + Click to'lov) =================

    public SendMessage showSubscription(User user) {
        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());

        if (user.hasRole("ROLE_OWNER")) {
            msg.setText("👑 Siz OWNER'siz — ADMIN obunasi kerak emas.");
            return msg;
        }

        Optional<SubscriptionDto> active = subscriptionService.listForUser(user.getId()).stream()
                .filter(s -> "CONFIRMED".equals(s.status()))
                .filter(s -> s.endDate() != null && s.endDate().isAfter(LocalDateTime.now()))
                .max(Comparator.comparing(SubscriptionDto::endDate));

        StringBuilder sb = new StringBuilder("💳 <b>Obuna holati</b>\n\n");

        if (active.isPresent()) {
            sb.append("✅ ADMIN huquqi faol\n");
            sb.append("Amal qilish muddati: ").append(active.get().endDate().format(DATE_FORMAT)).append("\n\n");
        } else {
            sb.append("❌ Faol ADMIN obunangiz yo'q.\n\n");
        }

        long priceSom = paymentOrderService.getPricePerMonthSom();
        sb.append("1 oy narxi: ").append(String.format("%,d", priceSom).replace(",", " ")).append(" so'm\n");

        if (clickService.isEnabled()) {
            sb.append("\nQuyidagi tugma orqali 1 oyga to'lov qilishingiz mumkin.");

            InlineKeyboardButton payBtn = new InlineKeyboardButton();
            payBtn.setText("💳 1 oyga to'lash (Click)");
            payBtn.setCallbackData("pay_click_1m");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(payBtn)));
            msg.setReplyMarkup(markup);
        } else {
            sb.append("\nOnlayn to'lov hozircha ulanmagan. /pay ").append(priceSom)
                    .append(" buyrug'i orqali OWNER'ga so'rov yuborishingiz mumkin.");
        }

        msg.setText(sb.toString());
        msg.setParseMode("HTML");
        return msg;
    }

    // "💳 1 oyga to'lash" tugmasi bosilganda — 1 oylik order yaratib,
    // Click checkout havolasini yuboradi (saytdagi startPayment('CLICK') bilan bir xil oqim).
    public SendMessage createClickPaymentLink(User user) {
        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());

        try {
            PaymentOrder order = paymentOrderService.createOrder(user, 1);
            String checkoutUrl = clickService.buildPayUrl(order, "/profile");
            msg.setText("💳 To'lovni yakunlash uchun havolani bosing:\n" + checkoutUrl);
        } catch (IllegalArgumentException | IllegalStateException e) {
            msg.setText("❌ " + e.getMessage());
        }

        return msg;
    }

    // ================= Kurslar =================

    public SendMessage showCourses(User user) {
        List<CourseDto> courses = courseService.listCatalog(user);

        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());

        if (courses.isEmpty()) {
            msg.setText("📚 Hozircha kurslar mavjud emas.");
            return msg;
        }

        StringBuilder sb = new StringBuilder("📚 <b>Mavjud kurslar</b>\n\n");
        for (CourseDto c : courses) {
            sb.append(c.subscribed() ? "✅ " : "🔒 ").append(escape(c.title()))
                    .append(" (").append(c.sectionCount()).append(" bo'lim)\n");
        }
        sb.append("\nBatafsil va obuna bo'lish uchun saytdagi /courses sahifasidan foydalaning.");

        msg.setText(sb.toString());
        msg.setParseMode("HTML");
        return msg;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
