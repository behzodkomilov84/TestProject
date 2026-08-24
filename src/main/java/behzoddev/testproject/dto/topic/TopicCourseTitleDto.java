package behzoddev.testproject.dto.topic;

// Bitta fan (Science) ichida — qaysi mavzular (Topic) biror KURS bo'limiga
// bog'langanini bulk (N+1 emas, bitta so'rov bilan) bilish uchun
// (TopicService.getTopicsByScienceId — topics.html'da "🔗 Kurs: ..."
// belgisini ko'rsatish).
public record TopicCourseTitleDto(Long topicId, String courseTitle) {
}
