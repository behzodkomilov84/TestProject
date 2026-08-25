package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.math.BigDecimal;
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
        BigDecimal price,
        int sectionCount,
        boolean subscribed, // joriy foydalanuvchi obuna bo'lganmi (yoki kurs bepul/canManage)
        LocalDateTime createdAt,
        // Ixtiyoriy — faqat "O'chirilganlar savati" ro'yxatida to'ldiriladi
        // (qachon o'chirilganini ko'rsatish uchun). Oddiy katalogda har
        // doim null (soft-deleted kurslar u yerda umuman ko'rinmaydi).
        LocalDateTime deletedAt
) {
}
