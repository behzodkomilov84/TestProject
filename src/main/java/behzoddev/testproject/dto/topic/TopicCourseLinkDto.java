package behzoddev.testproject.dto.topic;

// Mavzu (Topic) qaysi kurs bo'limiga bog'langanini bildiradi — test
// yaratish formasidagi "🔗 Mavzuga havola qo'shish" tugmasi shu orqali
// /courses/{courseId}/sections/{sectionId} havolasini quradi.
public record TopicCourseLinkDto(Long courseId, Long sectionId, String topicName) {
}
