package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Subscription;
import behzoddev.testproject.entity.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByStatusOrderByCreatedAtDesc(SubscriptionStatus status);

    List<Subscription> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Subscription> findAllByOrderByCreatedAtDesc();

    // Muddati o'tgan, lekin hali EXPIRED deb belgilanmagan obunalar
    // (kunlik scheduled job shularni topib yopadi).
    List<Subscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDateTime time);

    // Foydalanuvchining hozircha boshqa faol (muddati o'tmagan) obunasi
    // bor-yo'qligini tekshirish uchun (ADMIN rolini olib tashlashdan oldin).
    boolean existsByUser_IdAndStatusAndEndDateAfter(Long userId, SubscriptionStatus status, LocalDateTime time);

    // To'lov tarixi/hisobot sahifasi uchun: hozir faol obunalar soni.
    long countByStatusAndEndDateAfter(SubscriptionStatus status, LocalDateTime time);

    // To'lov tarixi/hisobot sahifasi uchun: tasdiq kutayotgan so'rovlar soni.
    long countByStatus(SubscriptionStatus status);

    // Muddati tez orada (masalan 3 kun ichida) tugaydigan faol obunalar —
    // kunlik eslatma job'i shularni topadi (SubscriptionReminderService).
    List<Subscription> findByStatusAndEndDateBetween(SubscriptionStatus status, LocalDateTime from, LocalDateTime to);
}
