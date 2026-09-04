package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

// Kurs sahifasi (dastur/curriculum) — bo'limlar sarlavhalari + holati.
@Builder
public record CourseDetailDto(
        Long id,
        String title,
        String description,
        String coverImageUrl,
        boolean published,
        boolean free,
        BigDecimal price,
        boolean subscribed,
        boolean requestPending, // foydalanuvchi obunaga so'rov yuborgan, OWNER hali ko'rib chiqmagan
        boolean canManage, // OWNER uchun tahrirlash tugmalarini ko'rsatish
        // "✏️ Tahrirlash" formasida Yo'nalish select'ini oldindan
        // to'ldirish uchun (courseDetail.js).
        Long fieldId,
        String fieldName,
        List<CourseSectionSummaryDto> sections
) {
}
