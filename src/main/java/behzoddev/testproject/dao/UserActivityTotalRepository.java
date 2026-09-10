package behzoddev.testproject.dao;

import behzoddev.testproject.entity.UserActivityTotal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserActivityTotalRepository extends JpaRepository<UserActivityTotal, Long> {

    // Qator hali mavjud bo'lmasa yaratadi, bo'lsa — ustiga QO'SHADI
    // (throttled flush — UserActivityTracker#flush). MySQL upsert —
    // @EmbeddedId'siz oddiy PK bo'lgani uchun ham shu yerda ham
    // sodda native so'rov qulayroq (JPQL "SET x = x + :delta" avval
    // qator MAVJUD bo'lishini talab qiladi, yangi foydalanuvchi uchun
    // hech narsa yangilamay jim qolardi).
    @Modifying
    @Query(value = "INSERT INTO user_activity_totals (user_id, total_seconds) VALUES (:userId, :delta) " +
            "ON DUPLICATE KEY UPDATE total_seconds = total_seconds + :delta", nativeQuery = true)
    void addSeconds(@Param("userId") Long userId, @Param("delta") long delta);
}
