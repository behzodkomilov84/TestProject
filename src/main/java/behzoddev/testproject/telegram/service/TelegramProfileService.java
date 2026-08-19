package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.profile.ChangeEmailDto;
import behzoddev.testproject.dto.profile.ChangePasswordDto;
import behzoddev.testproject.dto.profile.ChangePhoneDto;
import behzoddev.testproject.dto.profile.ChangeUsernameDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.PhoneNumberService;
import behzoddev.testproject.service.ProfileService;
import behzoddev.testproject.telegram.state.BotState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

// Botda "👤 Profil" bo'limi — ko'rish va tahrirlash (username/email/telefon/
// parol). Haqiqiy o'zgartirish saytdagi bilan bir xil ProfileService orqali
// bajariladi — validatsiya/xatolik xabarlari ikkala joyda ham bir xil.
@Service
@RequiredArgsConstructor
public class TelegramProfileService {

    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final PhoneNumberService phoneNumberService;
    private final TelegramSessionService sessionService;

    public SendMessage viewProfile(Long chatId) {
        User user = getUserByChatId(chatId);

        String rolesText = user.getRoles().stream()
                .map(Role::getRoleName)
                .map(r -> r.replace("ROLE_", ""))
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");

        String text = "👤 <b>Profil</b>\n\n" +
                "Foydalanuvchi nomi: " + escape(user.getUsername()) + "\n" +
                "Email: " + escape(user.getEmail() != null ? user.getEmail() : "— (kiritilmagan)") + "\n" +
                "Telefon: " + escape(phoneNumberDisplayOrPlaceholder(user.getPhoneNumber())) + "\n" +
                "Rol: " + rolesText;

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setParseMode("HTML");
        msg.setReplyMarkup(editButtons());
        return msg;
    }

    private InlineKeyboardMarkup editButtons() {
        InlineKeyboardButton editUsername = button("✏️ Ismni o'zgartirish", "profile_edit_username");
        InlineKeyboardButton editEmail = button("✏️ Emailni o'zgartirish", "profile_edit_email");
        InlineKeyboardButton editPhone = button("✏️ Telefonni o'zgartirish", "profile_edit_phone");
        InlineKeyboardButton editPassword = button("🔒 Parolni o'zgartirish", "profile_edit_password");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(editUsername),
                List.of(editEmail),
                List.of(editPhone),
                List.of(editPassword)
        ));
        return markup;
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    // ===== Tahrirlashni boshlash (inline tugma bosilganda) =====

    public SendMessage startEditUsername(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_USERNAME);
        return prompt(chatId, "✏️ Yangi foydalanuvchi nomini yozing (kamida 3 belgi):");
    }

    public SendMessage startEditEmail(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_EMAIL);
        return prompt(chatId, "✏️ Yangi email manzilingizni yozing:");
    }

    public SendMessage startEditPhone(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_PHONE);
        return prompt(chatId, "✏️ Yangi telefon raqamingizni yozing (masalan: 901234567 yoki +998901234567):");
    }

    public SendMessage startEditPassword(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_CURRENT_PASSWORD);
        return prompt(chatId,
                "🔒 Avval hozirgi parolingizni yozing.\n\n" +
                        "⚠️ Diqqat: Telegram xabarlar tarixi shifrlanmagan holda saqlanadi. " +
                        "Parolni yozgach, xabaringizni chatdan o'chirib tashlashni tavsiya qilamiz.");
    }

    private SendMessage prompt(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text + "\n\n(Bekor qilish uchun /cancel yozing)");
        return msg;
    }

    // ===== Kiritilgan matnni qayta ishlash (foydalanuvchi javob yozganda) =====

    public SendMessage handleAwaitingInput(Long chatId, BotState state, String text) {
        return switch (state) {
            case AWAITING_USERNAME -> applyUsername(chatId, text);
            case AWAITING_EMAIL -> applyEmail(chatId, text);
            case AWAITING_PHONE -> applyPhone(chatId, text);
            case AWAITING_CURRENT_PASSWORD -> applyCurrentPassword(chatId, text);
            case AWAITING_NEW_PASSWORD -> applyNewPassword(chatId, text);
            // NONE va profilga aloqasiz boshqa holatlar (masalan mustaqil test)
            // bu yerga chaqiruvchi (TelegramBot.route) tomonidan filtrlanadi —
            // shunga qaramay, `default` kelajakda yangi BotState qo'shilganda
            // shu switch'ni "exhaustive emas" xatosidan asraydi.
            default -> null;
        };
    }

    private SendMessage applyUsername(Long chatId, String text) {
        try {
            profileService.changeUsername(getUserByChatId(chatId), new ChangeUsernameDto(text.trim()));
            sessionService.clear(chatId);
            return success(chatId, "✅ Foydalanuvchi nomi o'zgartirildi: " + escape(text.trim()));
        } catch (ResponseStatusException e) {
            return retry(chatId, "❌ " + e.getReason());
        } catch (Exception e) {
            return retry(chatId, "❌ Foydalanuvchi nomi noto'g'ri (kamida 3 belgi bo'lishi kerak).");
        }
    }

    private SendMessage applyEmail(Long chatId, String text) {
        try {
            profileService.changeEmail(getUserByChatId(chatId), new ChangeEmailDto(text.trim()));
            sessionService.clear(chatId);
            return success(chatId, "✅ Email saqlandi: " + escape(text.trim()));
        } catch (ResponseStatusException e) {
            return retry(chatId, "❌ " + e.getReason());
        } catch (Exception e) {
            return retry(chatId, "❌ To'g'ri email kiriting (masalan: user@example.com).");
        }
    }

    private SendMessage applyPhone(Long chatId, String text) {
        try {
            profileService.changePhone(getUserByChatId(chatId), new ChangePhoneDto("UZ", text.trim()));
            sessionService.clear(chatId);
            return success(chatId, "✅ Telefon raqam saqlandi.");
        } catch (IllegalArgumentException e) {
            return retry(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            return retry(chatId, "❌ Telefon raqamda xatolik.");
        }
    }

    private SendMessage applyCurrentPassword(Long chatId, String text) {
        // Keyingi bosqichda solishtirish uchun vaqtincha saqlaymiz —
        // haqiqiy tekshiruv ProfileService.changePassword'da bo'ladi.
        sessionService.putTempData(chatId, "currentPassword", text);
        sessionService.setState(chatId, BotState.AWAITING_NEW_PASSWORD);
        return prompt(chatId, "🔒 Endi yangi parolingizni yozing (kamida 6 belgi):");
    }

    private SendMessage applyNewPassword(Long chatId, String text) {
        String currentPassword = sessionService.getTempData(chatId).get("currentPassword");

        try {
            profileService.changePassword(getUserByChatId(chatId), new ChangePasswordDto(currentPassword, text));
            sessionService.clear(chatId);
            return success(chatId, "✅ Parol muvaffaqiyatli o'zgartirildi.\n\n" +
                    "⚠️ Xavfsizlik uchun yuqoridagi parol xabarlarini chatdan o'chirib tashlashni unutmang.");
        } catch (ResponseStatusException e) {
            sessionService.clear(chatId);
            return retryFromStart(chatId, "❌ " + e.getReason());
        } catch (Exception e) {
            sessionService.clear(chatId);
            return retryFromStart(chatId, "❌ Yangi parol kamida 6 belgidan iborat bo'lishi kerak.");
        }
    }

    private SendMessage success(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        return msg;
    }

    private SendMessage retry(Long chatId, String errorText) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(errorText + "\n\nQayta urinib ko'ring, yoki /cancel yozing.");
        return msg;
    }

    private SendMessage retryFromStart(Long chatId, String errorText) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(errorText + "\n\nQaytadan boshlash uchun 👤 Profil bo'limiga o'ting.");
        return msg;
    }

    public SendMessage cancelFlow(Long chatId) {
        sessionService.clear(chatId);
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("❎ Bekor qilindi.");
        return msg;
    }

    private User getUserByChatId(Long chatId) {
        return userRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }

    private String phoneNumberDisplayOrPlaceholder(String e164) {
        String formatted = phoneNumberService.formatForDisplay(e164);
        return formatted != null ? formatted : "— (kiritilmagan)";
    }

    // Telegram HTML parse mode'da maxsus belgilar (< > &) muammo qilmasligi uchun.
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
