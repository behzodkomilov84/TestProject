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
        // Shu dars aynan bitta Topic'ga (test-bankdagi Dars) bog'langan bo'lsa
        // — kurs dasturi ro'yxatida ham "🎯 Darsga oid testlarni yechish"
        // tugmasini ko'rsatish uchun (avval faqat darsni ochgach ko'rinardi).
        Long linkedTopicId,
        Long linkedScienceId,
        // linkedTopicId bog'langan bo'lsa — shu mavzuning nechta faol
        // savoli borligi (kartochkada "N ta test" belgisi uchun) —
        // linkedTopicId null bo'lsa, bu ham null (savol soni ma'nosiz).
        Integer linkedTopicQuestionCount,
        // Kurs ICHIDAGI Bo'lim (CourseChapter) — null bo'lsa "Bo'limsiz",
        // frontend'da (courseDetail.js) shu bo'yicha alohida "box"larga
        // guruhlanadi.
        Long chapterId,
        String chapterName,
        Integer chapterOrderIndex
) {
}
