package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Subscription;
import behzoddev.testproject.entity.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByStatusOrderByCreatedAtDesc(SubscriptionStatus status);

    // Foydalanuvchini o'chirishdan oldin — "subscriptions.user_id" NOT NULL
    // FK RESTRICT (haqiqiy topilgan bug, 2026-09-07). O'zining obuna
    // yozuvlari — o'chirilib ketaveradi (sinov to'lovlari kabi).
    void deleteByUser_Id(Long userId);

    // "confirmed_by" NULL-ga ruxsat beriladi — bu foydalanuvchi OWNER/ADMIN
    // sifatida BOSHQA birovning obunasini tasdiqlagan bo'lishi mumkin,
    // o'sha to'lov tarixi yo'qolib qolmasin deb qator o'chirilmaydi,
    // faqat "kim tasdiqlagani" NULL qilinadi.
    @Modifying
    @Query("UPDATE Subscription s SET s.confirmedBy = NULL WHERE s.confirmedBy.id = :userId")
    void clearConfirmedBy(@Param("userId") Long userId);

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
