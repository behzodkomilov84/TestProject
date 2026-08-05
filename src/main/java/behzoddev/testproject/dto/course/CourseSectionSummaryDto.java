package behzoddev.testproject.dto.course;

import lombok.Builder;

// Kurs dasturi (curriculum) ro'yxatidagi bitta bo'lim — kontentisiz,
// faqat sarlavha + holat (qulflangan/tugatilgan).
@Builder
public record CourseSectionSummaryDto(
        Long id,
        String title,
        int orderIndex,
        String type,
        boolean locked,
        boolean completed
) {
}
