package behzoddev.testproject.dto.course;

// "Kurs ichidan mavzu yoritmasi bo'yicha qidiruv" natijasi — topics.html
// va courseDetail.js'dagi umumiy qidiruv funksiyasi uchun
// (CourseSectionRepository.searchLinkedExplanations,
// CourseService.searchTopicExplanations). Bosilganda frontend
// courseId+sectionId orqali to'g'ridan-to'g'ri o'sha kurs bo'limiga
// (/courses/{courseId}/sections/{sectionId}) o'tkazadi.
public record TopicExplanationSearchResultDto(
        Long topicId,
        String topicName,
        Long courseId,
        String courseTitle,
        Long sectionId,
        String sectionTitle
) {
}
