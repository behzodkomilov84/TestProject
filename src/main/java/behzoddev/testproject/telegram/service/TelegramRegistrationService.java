package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.user.RegisterDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.EmailVerificationService;
import behzoddev.testproject.service.PhoneNumberService;
import behzoddev.testproject.service.UserServiceImpl;
import behzoddev.testproject.telegram.state.BotState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Map;

// Botda to'g'ridan-to'g'ri ro'yxatdan o'tish — saytga kirmasdan. Haqiqiy
// UserServiceImpl.register()/EmailVerificationService orqali — saytdagi
// bilan bir xil validatsiya va oqim (email tasdiqlash kodi hamon EMAILGA
// yuboriladi, chunki ro'yxatdan o'tishda email majburiy va parolni
// tiklashda zaxira kanal sifatida ishlatiladi). Muvaffaqiyatli
// ro'yxatdan o'tib, email tasdiqlangach, shu Telegram akkaunt darhol
// (qo'shimcha /link kodi shart emas) yangi hisobga ulanadi — chunki
// foydalanuvchi aynan shu suhbatda o'z ma'lumotlarini kiritgan.
@Service
@RequiredArgsConstructor
public class TelegramRegistrationService {

    private final UserServiceImpl userServiceImpl;
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PhoneNumberService phoneNumberService;
    private final TelegramSessionService sessionService;
    private final TelegramMenuService menuService;

    public SendMessage start(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_REG_USERNAME);
        return prompt(chatId, "🆕 Ro'yxatdan o'tish\n\nFoydalanuvchi nomini (username) kamida 3 belgi bilan yozing:");
    }

    // ===== 1. Username =====

    public SendMessage applyUsername(Long chatId, String text) {
        String username = text.trim();

        if (username.length() < 3) {
            return retry(chatId, "❌ Username kamida 3 belgi bo'lishi kerak.");
        }
        if (userRepository.existsByUsername(username)) {
            return retry(chatId, "❌ Bu username allaqachon band. Boshqasini kiriting.");
        }

        sessionService.putTempData(chatId, "reg_username", username);
        sessionService.setState(chatId, BotState.AWAITING_REG_EMAIL);
        return prompt(chatId, "📧 Endi email manzilingizni yozing (tasdiqlash kodi shu yerga yuboriladi):");
    }

    // ===== 2. Email =====

    public SendMessage applyEmail(Long chatId, String text) {
        String email = text.trim();

        if (!email.contains("@") || !email.contains(".")) {
            return retry(chatId, "❌ To'g'ri email manzil kiriting (masalan: user@example.com).");
        }
        if (userRepository.existsByEmail(email)) {
            return retry(chatId, "❌ Bu email allaqachon ro'yxatdan o'tgan.");
        }

        sessionService.putTempData(chatId, "reg_email", email);
        sessionService.setState(chatId, BotState.AWAITING_REG_PHONE);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("📱 Telefon raqamingizni yozing (masalan: 901234567), yoki o'tkazib yuboring:\n\n(Bekor qilish uchun /cancel)");

        InlineKeyboardButton skip = button("⏭ O'tkazib yuborish", "reg_skip_phone");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(skip)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    // ===== 3. Telefon (ixtiyoriy) =====

    public SendMessage applyPhone(Long chatId, String text) {
        try {
            String normalized = phoneNumberService.normalize("UZ", text.trim());
            sessionService.putTempData(chatId, "reg_phone", normalized);
        } catch (IllegalArgumentException e) {
            return retry(chatId, "❌ " + e.getMessage());
        }

        return moveToPassword(chatId);
    }

    public SendMessage skipPhone(Long chatId) {
        return moveToPassword(chatId);
    }

    private SendMessage moveToPassword(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_REG_PASSWORD);
        return prompt(chatId, "🔒 Parol o'ylab toping (kamida 6 belgi):");
    }

    // ===== 4-5. Parol =====

    public SendMessage applyPassword(Long chatId, String text) {
        if (text.length() < 6) {
            return retry(chatId, "❌ Parol kamida 6 belgidan iborat bo'lishi kerak.");
        }

        sessionService.putTempData(chatId, "reg_password", text);
        sessionService.setState(chatId, BotState.AWAITING_REG_CONFIRM_PASSWORD);
        return prompt(chatId, "🔒 Parolni tasdiqlash uchun yana bir marta yozing:");
    }

    public SendMessage applyConfirmPassword(Long chatId, String text) {
        String password = sessionService.getTempData(chatId).get("reg_password");

        if (!text.equals(password)) {
            return retry(chatId, "❌ Parollar mos kelmadi. Parolni qaytadan yozing (avvalgi bosqichdan boshlanadi).");
        }

        sessionService.setState(chatId, BotState.AWAITING_REG_TERMS);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("📄 Davom etish uchun <a href=\"https://study-grow.uz/terms\">Foydalanish shartlari</a> va " +
                "<a href=\"https://study-grow.uz/privacy\">Maxfiylik siyosati</a>ga rozimisiz?");
        msg.setParseMode("HTML");

        InlineKeyboardButton agree = button("✅ Roziman, ro'yxatdan o'taman", "reg_terms_yes");
        InlineKeyboardButton cancel = button("❌ Bekor qilish", "reg_terms_no");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(agree), List.of(cancel)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    // ===== 6. Shartlarga rozilik -> haqiqiy ro'yxatdan o'tish =====

    public SendMessage confirmTerms(Long chatId) {
        Map<String, String> data = sessionService.getTempData(chatId);

        String username = data.get("reg_username");
        String email = data.get("reg_email");
        String phone = data.get("reg_phone"); // null bo'lishi mumkin — ixtiyoriy
        String password = data.get("reg_password");

        RegisterDto dto = new RegisterDto(username, email, "UZ", phone, password, password);

        try {
            userServiceImpl.register(dto);
        } catch (Exception e) {
            sessionService.clear(chatId);
            return success(chatId, "❌ Ro'yxatdan o'tishda xatolik: " + e.getMessage() +
                    "\n\nQaytadan boshlash uchun /start yozing.");
        }

        // Ro'yxatdan o'tish muvaffaqiyatli — shu Telegram akkauntni darhol
        // yangi hisobga ulaymiz (qo'shimcha /link kodi shart emas, chunki
        // foydalanuvchi aynan shu suhbatning o'zida ma'lumotlarini kiritdi).
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Ro'yxatdan o'tgan foydalanuvchi topilmadi"));
        user.setTelegramId(chatId);
        userRepository.save(user);

        sessionService.setState(chatId, BotState.AWAITING_REG_EMAIL_CODE);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("✅ Ro'yxatdan o'tdingiz!\n\n📧 Emailingizga (" + maskEmail(email) +
                ") 6 xonali tasdiqlash kodi yuborildi. Kodni shu yerga yozing:");

        InlineKeyboardButton resend = button("🔁 Kodni qayta yuborish", "reg_resend_code");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(resend)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage remindToTapTermsButton(Long chatId) {
        return success(chatId, "👆 Iltimos, yuqoridagi tugmalardan birini bosing (yoki /cancel).");
    }

    public SendMessage cancelTerms(Long chatId) {
        sessionService.clear(chatId);
        return success(chatId, "❎ Ro'yxatdan o'tish bekor qilindi.");
    }

    // ===== 7. Email tasdiqlash kodi =====

    public SendMessage applyEmailCode(Long chatId, String text) {
        String username = sessionService.getTempData(chatId).get("reg_username");

        try {
            emailVerificationService.confirm(username, text.trim());
        } catch (Exception e) {
            return retry(chatId, "❌ " + e.getMessage());
        }

        sessionService.clear(chatId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Foydalanuvchi topilmadi"));

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("🎉 Email tasdiqlandi! Endi saytga ham, botga ham to'liq kirishingiz mumkin.\n\n" +
                menuService.welcomeText(user));
        msg.setReplyMarkup(menuService.buildMainMenu(user));
        return msg;
    }

    public SendMessage resendCode(Long chatId) {
        String username = sessionService.getTempData(chatId).get("reg_username");

        try {
            String result = emailVerificationService.resend(username);
            return success(chatId, result);
        } catch (Exception e) {
            return success(chatId, "❌ " + e.getMessage());
        }
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
        msg.setText(text + "\n\n(Bekor qilish uchun /cancel)");
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

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
