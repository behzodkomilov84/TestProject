package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUser_IdAndReadFalse(Long userId);

    // "Yangi" / "O'qilgan" sahifasi uchun — cheklovsiz (top-50 emas), status
    // bo'yicha to'liq ro'yxat.
    List<Notification> findByUser_IdAndReadOrderByCreatedAtDesc(Long userId, boolean read);

    long countByUser_IdAndReadFalse(Long userId);

    long countByUser_IdAndReadTrue(Long userId);
}
