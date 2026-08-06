package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.SubscriptionRepository;
import behzoddev.testproject.entity.Subscription;
import behzoddev.testproject.entity.enums.SubscriptionStatus;
import behzoddev.testproject.service.NotificationService;
import behzoddev.testproject.telegram.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// ADMIN obunasi muddati tugashiga 3 kun qolganda Telegram orqali (va
// saytdagi bildirishnoma markazi orqali) eslatma yuboradi —
// DeadlineReminderService bilan bir xil pattern: kunlik cron
// "endDate BETWEEN now AND +3 kun" oralig'ini tekshiradi, shuning uchun
// har bir obuna uchun aynan bitta marta (muddat 3 kunlik oynaga
// kirgan kuni) eslatma boradi — alohida "yuborildimi" belgisi shart emas.
@Service
@RequiredArgsConstructor
public class SubscriptionReminderService {

    private static final int REMINDER_DAYS_BEFORE = 3;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SubscriptionRepository subscriptionRepository;
    private final TelegramBot telegramBot;
    private final NotificationService notificationService;

    @SneakyThrows
    @Scheduled(cron = "0 0 9 * * *") // har kuni 09:00'da
    public void sendExpiryReminders() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(REMINDER_DAYS_BEFORE);

        List<Subscription> expiringSoon = subscriptionRepository
                .findByStatusAndEndDateBetween(SubscriptionStatus.CONFIRMED, now, threshold);

        for (Subscription subscription : expiringSoon) {

            var user = subscription.getUser();
            String endDateText = subscription.getEndDate().format(DATE_FORMAT);

            notificationService.create(user,
                    "⏳ ADMIN obunangiz " + endDateText + " sanasida tugaydi. " +
                            "Uzluksiz foydalanish uchun to'lovni yangilashni unutmang.",
                    "/profile");

            Long telegramId = user.getTelegramId();
            if (telegramId == null) continue;

            SendMessage msg = new SendMessage();
            msg.setChatId(telegramId.toString());
            msg.setText(
                    "⏳ Eslatma!\n\n" +
                            "ADMIN obunangiz muddati tez orada (" + endDateText + ") tugaydi." +
                            "\nUzluksiz foydalanish uchun to'lovni yangilashni unutmang."
            );

            telegramBot.execute(msg);
        }
    }
}
