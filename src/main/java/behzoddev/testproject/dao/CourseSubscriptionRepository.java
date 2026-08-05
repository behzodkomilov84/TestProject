package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseSubscription;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseSubscriptionRepository extends JpaRepository<CourseSubscription, Long> {

    boolean existsByUser_IdAndCourse_IdAndStatus(Long userId, Long courseId, CourseSubscriptionStatus status);

    Optional<CourseSubscription> findByUser_IdAndCourse_IdAndStatus(Long userId, Long courseId, CourseSubscriptionStatus status);

    List<CourseSubscription> findByCourse_IdOrderByCreatedAtDesc(Long courseId);

    List<CourseSubscription> findAllByOrderByCreatedAtDesc();
}
