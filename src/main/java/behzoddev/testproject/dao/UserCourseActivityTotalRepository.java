package behzoddev.testproject.dao;

import behzoddev.testproject.entity.UserCourseActivityTotal;
import behzoddev.testproject.entity.compositeKey.UserCourseActivityKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCourseActivityTotalRepository extends JpaRepository<UserCourseActivityTotal, UserCourseActivityKey> {

    // UserActivityTotalRepository.addSeconds bilan bir xil g'oya —
    // MySQL upsert (qator mavjud bo'lmasa yaratadi, bo'lsa ustiga qo'shadi).
    @Modifying
    @Query(value = "INSERT INTO user_course_activity_totals (user_id, course_id, total_seconds) VALUES (:userId, :courseId, :delta) " +
            "ON DUPLICATE KEY UPDATE total_seconds = total_seconds + :delta", nativeQuery = true)
    void addSeconds(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("delta") long delta);

    // "/users" sahifasidagi "Oxirgi tashrif" oynasida — eng ko'p vaqt
    // ketgan kursdan boshlab ko'rsatish uchun.
    List<UserCourseActivityTotal> findById_UserIdOrderByTotalSecondsDesc(Long userId);
}
