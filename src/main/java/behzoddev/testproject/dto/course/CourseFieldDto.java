package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.time.LocalDateTime;

// "Yo'nalish" (soha) — katalog kartochkasi uchun (coursesCatalog.js).
@Builder
public record CourseFieldDto(
        Long id,
        String name,
        int orderIndex,
        int courseCount, // shu Yo'nalishdagi FAOL (o'chirilmagan) Bo'limlar (Course) soni
        LocalDateTime createdAt,
        // Ixtiyoriy — faqat "O'chirilganlar savati" ro'yxatida to'ldiriladi.
        LocalDateTime deletedAt
) {
}
