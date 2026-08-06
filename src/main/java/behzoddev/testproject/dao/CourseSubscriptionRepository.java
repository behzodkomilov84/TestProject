package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseSubscription;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CourseSubscriptionRepository extends JpaRepository<CourseSubscription, Long> {

    boolean existsByUser_IdAndCourse_IdAndStatus(Long userId, Long courseId, CourseSubscriptionStatus status);

    // Haqiqiy (real-time) kirish tekshiruvi uchun — status=CONFIRMED bo'lsa-da,
    // kunlik expireSubscriptions() job'i hali ishlamagan bo'lishi mumkin,
    // shuning uchun endDate to'g'ridan-to'g'ri tekshiriladi.
    boolean existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
            Long userId, Long courseId, CourseSubscriptionStatus status, LocalDateTime time);

    Optional<CourseSubscription> findByUser_IdAndCourse_IdAndStatus(Long userId, Long courseId, CourseSubscriptionStatus status);

    List<CourseSubscription> findByCourse_IdOrderByCreatedAtDesc(Long courseId);

    List<CourseSubscription> findAllByOrderByCreatedAtDesc();

    // Muddati o'tgan, lekin hali EXPIRED deb belgilanmagan kurs obunalari
    // (kunlik scheduled job shularni topib yopadi).
    List<CourseSubscription> findByStatusAndEndDateBefore(CourseSubscriptionStatus status, LocalDateTime time);
}
