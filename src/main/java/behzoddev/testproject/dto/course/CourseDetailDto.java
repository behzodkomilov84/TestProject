package behzoddev.testproject.dto.course;

import lombok.Builder;

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
        boolean subscribed,
        boolean requestPending, // foydalanuvchi obunaga so'rov yuborgan, OWNER hali ko'rib chiqmagan
        boolean canManage, // OWNER uchun tahrirlash tugmalarini ko'rsatish
        List<CourseSectionSummaryDto> sections
) {
}
