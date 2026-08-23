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
        boolean completed,
        // Shu bo'lim aynan bitta mavzuga (Topic) bog'langan bo'lsa — kurs
        // dasturi ro'yxatida ham "🎯 Mavzuga oid testlarni yechish"
        // tugmasini ko'rsatish uchun (avval faqat bo'limni ochgach ko'rinardi).
        Long linkedTopicId,
        Long linkedScienceId,
        // Kurs ICHIDAGI Bo'lim (CourseChapter) — null bo'lsa "Bo'limsiz",
        // frontend'da (courseDetail.js) shu bo'yicha alohida "box"larga
        // guruhlanadi.
        Long chapterId,
        String chapterName,
        Integer chapterOrderIndex
) {
}
