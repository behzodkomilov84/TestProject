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
}
