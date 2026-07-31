package behzoddev.testproject.service;

import behzoddev.testproject.dao.PasswordResetCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.PasswordResetCode;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.PasswordResetChannel;
import behzoddev.testproject.exception.PasswordsDoNotMatchException;
import behzoddev.testproject.telegram.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Parolni tiklash — foydalanuvchi tasdiqlash kodini ikkita kanaldan biri
 * orqali oladi: agar Telegram ulangan bo'lsa (ustuvor, chunki bepul va
 * darhol yetib boradi), aks holda ro'yxatdan o'tishda kiritilgan email
 * orqali. Kod 10 daqiqa amal qiladi va faqat bir marta ishlatiladi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int CODE_TTL_MINUTES = 10;

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TelegramBot telegramBot;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String requestReset(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("❌Bunday foydalanuvchi topilmadi."));

        String code = generateCode();
        PasswordResetChannel channel = user.getTelegramId() != null
                ? PasswordResetChannel.TELEGRAM
                : PasswordResetChannel.EMAIL;

        if (channel == PasswordResetChannel.EMAIL && (user.getEmail() == null || user.getEmail().isBlank())) {
            throw new IllegalArgumentException(
                    "❌Sizda na Telegram, na email ulanmagan. Administrator bilan bog'laning.");
        }

        PasswordResetCode resetCode = PasswordResetCode.builder()
                .user(user)
                .code(code)
                .channel(channel)
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                .build();

        passwordResetCodeRepository.save(resetCode);

        if (channel == PasswordResetChannel.TELEGRAM) {
            sendViaTelegram(user.getTelegramId(), code);
            return "✅ Tasdiqlash kodi Telegram orqali yuborildi.";
        }

        boolean sent = emailService.sendPasswordResetCode(user.getEmail(), code);
        return sent
                ? "✅ Tasdiqlash kodi email manzilingizga yuborildi: " + maskEmail(user.getEmail())
                : "❌ Email yuborishda xatolik yuz berdi. Administrator bilan bog'laning.";
    }

    @Transactional
    public void confirmReset(String username, String code, String newPassword, String confirmPassword) {

        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordsDoNotMatchException("Parollar mos kelmadi");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("❌Parol kamida 6 xonali bo'lishi kerak.");
        }

        PasswordResetCode resetCode = passwordResetCodeRepository
                .findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(username, code, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("❌Kod noto'g'ri yoki muddati o'tgan."));

        User user = resetCode.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetCode.setUsed(true);
        passwordResetCodeRepository.save(resetCode);

        log.info("Parol muvaffaqiyatli tiklandi: {}", user.getUsername());
    }

    private void sendViaTelegram(Long chatId, String code) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔑 Parolni tiklash kodi: " + code + "\n\n" +
                "Kod 10 daqiqa amal qiladi. Agar bu so'rovni siz yubormagan bo'lsangiz, e'tiborsiz qoldiring.");
        try {
            telegramBot.execute(message);
        } catch (Exception e) {
            log.error("Telegram orqali parolni tiklash kodini yuborishda xatolik", e);
        }
    }

    private String generateCode() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
