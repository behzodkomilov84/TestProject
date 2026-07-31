package behzoddev.testproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Parolni tiklash kodini email orqali yuborish uchun ingichka wrapper.
 * SMTP sozlamalari bo'sh bo'lsa ham ilova ishga tushishi kerak (Telegram
 * ustuvor kanal) — shu sabab yuborishda xatolik bo'lsa ham exception
 * yutib qo'yiladi, faqat log'ga yoziladi va chaqiruvchi tomon xabardor
 * qilinadi (false qaytariladi).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public boolean sendPasswordResetCode(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Parolni tiklash kodi");
            message.setText(
                    "Parolni tiklash uchun kod: " + code + "\n\n" +
                            "Kod 10 daqiqa amal qiladi. Agar bu so'rovni siz yubormagan bo'lsangiz, " +
                            "shunchaki bu xabarni e'tiborsiz qoldiring."
            );

            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Parolni tiklash kodini email orqali yuborishda xatolik: {}", e.getMessage(), e);
            return false;
        }
    }
}
