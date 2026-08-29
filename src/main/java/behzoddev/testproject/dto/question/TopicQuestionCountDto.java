package behzoddev.testproject.dto.question;

// Bitta mavzuning (Topic) nechta faol savoli borligi — kurs sahifasida
// ("Mavzu kartochkasi") har bir bog'langan mavzu uchun BULK (bitta
// so'rov, N+1 emas) ko'rsatish uchun (CourseService.getDetail,
// QuestionRepository.countByTopicIdsGrouped).
public record TopicQuestionCountDto(Long topicId, long count) {
}
