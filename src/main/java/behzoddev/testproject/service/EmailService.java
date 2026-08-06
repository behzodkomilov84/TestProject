package behzoddev.testproject.service;

import behzoddev.testproject.dto.subscription.MonthlyRevenueDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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

    // Ro'yxatdan o'tishda email tasdiqlash kodi.
    public boolean sendVerificationCode(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Emailni tasdiqlash");
            message.setText(
                    "Ro'yxatdan o'tishni yakunlash uchun tasdiqlash kodi: " + code + "\n\n" +
                            "Kod 30 daqiqa amal qiladi. Agar bu ro'yxatdan o'tishni siz boshlamagan bo'lsangiz, " +
                            "shunchaki bu xabarni e'tiborsiz qoldiring."
            );

            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Tasdiqlash kodini email orqali yuborishda xatolik: {}", e.getMessage(), e);
            return false;
        }
    }

    // OWNER uchun to'lov tarixi/hisobot sahifasidagi statistikani email orqali yuborish.
    public boolean sendSubscriptionReport(String to, SubscriptionStatsDto stats) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("To'lov tarixi hisoboti — " + LocalDate.now().format(DATE_FORMAT));

            StringBuilder body = new StringBuilder();
            body.append("To'lov tarixi hisoboti\n\n");
            body.append("Jami tushum: ").append(stats.totalRevenue()).append(" so'm\n");
            body.append("Shu oy: ").append(stats.thisMonthRevenue()).append(" so'm\n");
            body.append("Faol obunachilar: ").append(stats.activeSubscribersCount()).append("\n");
            body.append("Jami tasdiqlangan to'lovlar: ").append(stats.totalConfirmedCount()).append("\n");
            body.append("Tasdiq kutayotgan so'rovlar: ").append(stats.pendingCount()).append("\n\n");
            body.append("Oylar bo'yicha tushum:\n");

            for (MonthlyRevenueDto m : stats.monthlyBreakdown()) {
                body.append("- ").append(m.month()).append(": ")
                        .append(m.amount()).append(" so'm (").append(m.count()).append(" ta to'lov)\n");
            }

            message.setText(body.toString());

            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Hisobotni email orqali yuborishda xatolik: {}", e.getMessage(), e);
            return false;
        }
    }
}
