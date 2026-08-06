package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.SubscriptionRepository;
import behzoddev.testproject.entity.Subscription;
import behzoddev.testproject.entity.enums.SubscriptionStatus;
import behzoddev.testproject.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// ADMIN obunasi muddati tugashiga 3 kun qolganda Telegram orqali (va
// saytdagi bildirishnoma markazi orqali) eslatma yuboradi —
// DeadlineReminderService bilan bir xil pattern: kunlik cron
// "endDate BETWEEN now AND +3 kun" oralig'ini tekshiradi, shuning uchun
// har bir obuna uchun aynan bitta marta (muddat 3 kunlik oynaga
// kirgan kuni) eslatma boradi — alohida "yuborildimi" belgisi shart emas.
// Telegram'ga yuborish NotificationService.create() ichida avtomatik
// amalga oshadi (foydalanuvchi botga ulangan bo'lsa).
@Service
@RequiredArgsConstructor
public class SubscriptionReminderService {

    private static final int REMINDER_DAYS_BEFORE = 3;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;

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
        }
    }
}
