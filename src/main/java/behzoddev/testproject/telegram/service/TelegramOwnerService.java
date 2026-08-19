package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.SubscriptionService;
import behzoddev.testproject.service.UserServiceImpl;
import behzoddev.testproject.service.payment.PaymentOrderService;
import behzoddev.testproject.telegram.state.BotState;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Botda OWNER uchun: 👑 Foydalanuvchilar (rol berish/olib tashlash, blokdan
// chiqarish), 💰 To'lovlar (kutilayotgan so'rovlarni tasdiqlash/rad etish),
// ⚙️ Tizim sozlamalari (Click minimal summasi), 📢 E'lon yuborish. Haqiqiy
// UserServiceImpl/SubscriptionService/PaymentOrderService orqali — saytdagi
// /users sahifasi bilan bir xil biznes-logika.
@Service
@RequiredArgsConstructor
public class TelegramOwnerService {

    private static final List<String> ALL_ROLES = List.of("ROLE_OWNER", "ROLE_ADMIN", "ROLE_USER");

    private final SubscriptionService subscriptionService;
    private final UserServiceImpl userServiceImpl;
    private final UserRepository userRepository;
    private final PaymentOrderService paymentOrderService;
    private final TelegramSessionService sessionService;

    // ================= 👑 Foydalanuvchilar =================

    public SendMessage startUserSearch(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_USER_SEARCH);
        return prompt(chatId, "✏️ Qidirmoqchi bo'lgan foydalanuvchining username'ini yozing:");
    }

    public SendMessage applyUserSearch(Long chatId, String text) {
        User target = userRepository.findByUsername(text.trim()).orElse(null);
        sessionService.clear(chatId);

        if (target == null) {
            return success(chatId, "❌ Bunday foydalanuvchi topilmadi: " + escape(text.trim()));
        }
        return userCard(chatId, target.getId());
    }

    public SendMessage userCard(Long chatId, Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));

        List<String> currentRoles = target.getRoles().stream().map(Role::getRoleName).sorted().toList();

        StringBuilder sb = new StringBuilder("👤 <b>" + escape(target.getUsername()) + "</b>\n");
        sb.append(target.isAccountNonLocked() ? "🔓 Ochiq" : "🔒 Bloklangan").append("\n\n");
        sb.append("Rollar:\n");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String role : ALL_ROLES) {
            boolean has = currentRoles.contains(role);
            String label = (has ? "✅ " : "⬜ ") + role.replace("ROLE_", "");
            String callback = "tg_role" + (has ? "del_" : "add_") + userId + "_" + role;
            rows.add(List.of(button(label, callback)));
        }
        if (!target.isAccountNonLocked()) {
            rows.add(List.of(button("🔓 Blokdan chiqarish", "tg_unlock_" + userId)));
        }

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());
        msg.setParseMode("HTML");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage toggleRole(Long chatId, Long targetUserId, String roleName, boolean add) {
        User owner = getUserByChatId(chatId);
        Authentication auth = new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities());

        try {
            if (add) {
                userServiceImpl.addRole(targetUserId, roleName, auth);
            } else {
                userServiceImpl.removeRole(targetUserId, roleName, auth);
            }
        } catch (Exception e) {
            return success(chatId, "❌ " + e.getMessage());
        }

        return userCard(chatId, targetUserId);
    }

    public SendMessage unlockUser(Long chatId, Long targetUserId) {
        userServiceImpl.unlockUser(targetUserId);
        return userCard(chatId, targetUserId);
    }

    // ================= 💰 To'lovlar =================

    public SendMessage listPendingPayments(User owner) {
        List<SubscriptionDto> pending = subscriptionService.listPending();
        SubscriptionStatsDto stats = subscriptionService.getStats();

        SendMessage msg = new SendMessage();
        msg.setChatId(owner.getTelegramId().toString());

        StringBuilder sb = new StringBuilder("💰 <b>To'lovlar</b>\n\n");
        sb.append("Jami tushum: ").append(formatSom(stats.totalRevenue())).append("\n");
        sb.append("Shu oy: ").append(formatSom(stats.thisMonthRevenue())).append("\n");
        sb.append("Faol obunachilar: ").append(stats.activeSubscribersCount()).append("\n");
        sb.append("Kutilayotgan so'rovlar: ").append(stats.pendingCount()).append("\n");

        msg.setParseMode("HTML");

        if (pending.isEmpty()) {
            sb.append("\nHozircha kutilayotgan so'rov yo'q.");
            msg.setText(sb.toString());
            return msg;
        }

        sb.append("\nKo'rib chiqish uchun so'rovni tanlang:");
        msg.setText(sb.toString());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (SubscriptionDto s : pending) {
            rows.add(List.of(button(s.username() + " — " + formatSom(s.amount()), "tg_paydetail_" + s.id())));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage showPaymentDetail(Long chatId, Long subscriptionId) {
        SubscriptionDto s = subscriptionService.listPending().stream()
                .filter(p -> p.id().equals(subscriptionId))
                .findFirst()
                .orElse(null);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (s == null) {
            msg.setText("⚠️ Bu so'rov allaqachon ko'rib chiqilgan.");
            return msg;
        }

        msg.setText("💰 <b>" + escape(s.username()) + "</b>\n" +
                "Summa: " + formatSom(s.amount()) + "\n" +
                "Manba: " + s.source() + "\n" +
                (s.note() != null ? "Izoh: " + escape(s.note()) + "\n" : ""));
        msg.setParseMode("HTML");

        InlineKeyboardButton confirm = button("✅ Tasdiqlash", "tg_payok_" + subscriptionId);
        InlineKeyboardButton reject = button("❌ Rad etish", "tg_payno_" + subscriptionId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(confirm, reject)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage confirmPayment(Long chatId, Long subscriptionId) {
        User owner = getUserByChatId(chatId);
        try {
            subscriptionService.confirm(subscriptionId, null, owner);
            return success(chatId, "✅ To'lov tasdiqlandi, ADMIN huquqi berildi.");
        } catch (Exception e) {
            return success(chatId, "❌ " + e.getMessage());
        }
    }

    public SendMessage rejectPayment(Long chatId, Long subscriptionId) {
        try {
            subscriptionService.cancel(subscriptionId);
            return success(chatId, "❌ So'rov rad etildi.");
        } catch (Exception e) {
            return success(chatId, "❌ " + e.getMessage());
        }
    }

    // BigDecimal.toBigInteger'dan qo'lda guruhlash — String.format("%,.0f", ...)
    // JVM'ning standart lokaliga qarab vergul o'rniga NBSP (U+00A0) yoki
    // boshqa belgi ishlatishi mumkin edi (production konteynerining
    // lokali ma'lum emas) — shu tufayli oddiy, lokaldan mustaqil bo'shliq
    // bilan o'zimiz guruhlaymiz.
    private String formatSom(BigDecimal amount) {
        if (amount == null) return "0 so'm";

        long value = amount.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        String digits = Long.toString(Math.abs(value));

        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            grouped.append(digits.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) grouped.append(' ');
        }

        return (value < 0 ? "-" : "") + grouped.reverse() + " so'm";
    }

    // ================= ⚙️ Tizim sozlamalari =================

    public SendMessage showSettings(Long chatId) {
        BigDecimal minAmount = paymentOrderService.getMinAmountSom();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("⚙️ <b>Tizim sozlamalari</b>\n\n" +
                "Click minimal tranzaksiya summasi: " + formatSom(minAmount));
        msg.setParseMode("HTML");

        InlineKeyboardButton edit = button("✏️ O'zgartirish", "tg_settings_edit");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(edit)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage startEditMinAmount(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_MIN_AMOUNT);
        return prompt(chatId, "✏️ Yangi minimal summani (so'm, faqat raqam) yozing:");
    }

    public SendMessage applyMinAmount(Long chatId, String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim().replace(" ", ""));
            paymentOrderService.updateMinAmountSom(value);
            sessionService.clear(chatId);
            return success(chatId, "✅ Minimal summa saqlandi: " + formatSom(value));
        } catch (NumberFormatException e) {
            return retry(chatId, "❌ Faqat raqam kiriting (masalan: 1000).");
        } catch (Exception e) {
            return retry(chatId, "❌ " + e.getMessage());
        }
    }

    // ================= 📢 E'lon yuborish =================

    public SendMessage startBroadcast(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_BROADCAST_TEXT);
        return prompt(chatId, "✏️ Barcha foydalanuvchilarga yubormoqchi bo'lgan e'lon matnini yozing:");
    }

    public SendMessage previewBroadcast(Long chatId, String text) {
        sessionService.putTempData(chatId, "tg_broadcastText", text);

        long recipientCount = userRepository.findAll().stream()
                .filter(u -> u.getTelegramId() != null)
                .count();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("📢 <b>Quyidagi e'lon " + recipientCount + " ta foydalanuvchiga yuboriladi:</b>\n\n" +
                escape(text));
        msg.setParseMode("HTML");

        InlineKeyboardButton send = button("✅ Yuborish", "tg_broadcast_yes");
        InlineKeyboardButton cancel = button("❌ Bekor qilish", "tg_broadcast_no");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(send, cancel)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    // Har bir qabul qiluvchiga bitta SendMessage tayyorlaydi (ro'yxatning
    // oxirgi elementi — OWNER'ning o'ziga "yuborildi" tasdiqlovi). Haqiqiy
    // yuborishni (execute) faqat TelegramBot bajara oladi.
    public List<SendMessage> buildBroadcastMessages(Long chatId) {
        String text = sessionService.getTempData(chatId).get("tg_broadcastText");
        sessionService.clear(chatId);

        List<SendMessage> messages = new ArrayList<>();
        if (text == null) {
            messages.add(success(chatId, "❌ E'lon matni topilmadi."));
            return messages;
        }

        List<User> recipients = userRepository.findAll().stream()
                .filter(u -> u.getTelegramId() != null)
                .toList();

        for (User u : recipients) {
            SendMessage m = new SendMessage();
            m.setChatId(u.getTelegramId().toString());
            m.setText("📢 " + text);
            messages.add(m);
        }

        messages.add(success(chatId, "✅ E'lon " + recipients.size() + " ta foydalanuvchiga yuborildi."));
        return messages;
    }

    public SendMessage cancelBroadcast(Long chatId) {
        sessionService.clear(chatId);
        return success(chatId, "❎ E'lon bekor qilindi.");
    }

    // ================= Yordamchi metodlar =================

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private SendMessage prompt(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text + "\n\n(Bekor qilish uchun /cancel yozing)");
        return msg;
    }

    private SendMessage retry(Long chatId, String errorText) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(errorText + "\n\nQayta urinib ko'ring, yoki /cancel yozing.");
        return msg;
    }

    private SendMessage success(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        return msg;
    }

    public SendMessage cancelFlow(Long chatId) {
        sessionService.clear(chatId);
        return success(chatId, "❎ Bekor qilindi.");
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
