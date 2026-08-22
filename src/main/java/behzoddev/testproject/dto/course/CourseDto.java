package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.time.LocalDateTime;

// Kurs katalogi (ro'yxat) uchun — bo'lim kontentisiz, umumiy ma'lumot.
@Builder
public record CourseDto(
        Long id,
        String title,
        String description,
        String coverImageUrl,
        boolean published,
        boolean free,
        int sectionCount,
        boolean subscribed, // joriy foydalanuvchi obuna bo'lganmi (yoki kurs bepul/canManage)
        LocalDateTime createdAt
) {
}
