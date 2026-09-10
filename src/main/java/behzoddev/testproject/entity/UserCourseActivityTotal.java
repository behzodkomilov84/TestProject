package behzoddev.testproject.entity;

import behzoddev.testproject.entity.compositeKey.UserCourseActivityKey;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Foydalanuvchi bitta KURSGA aloqador sahifalarda (/courses/{id}...,
// /api/courses/{id}...) qancha "faol" vaqt o'tkazgani (foydalanuvchi
// so'rovi, 2026-09-10: "Kurslar kesimida qancha soatlari kursga
// aloqador sahifalarda ketyapti?"). UserActivityTracker orqali to'planadi.
@Entity
@Table(name = "user_course_activity_totals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCourseActivityTotal {

    @EmbeddedId
    private UserCourseActivityKey id;

    @Column(name = "total_seconds", nullable = false)
    @Builder.Default
    private long totalSeconds = 0;
}
