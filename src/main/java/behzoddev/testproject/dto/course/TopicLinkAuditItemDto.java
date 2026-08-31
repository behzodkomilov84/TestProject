package behzoddev.testproject.dto.course;

// Bitta savolning to'g'ri javob izohidagi mavzu havolasi NOTO'G'RI
// (boshqa mavzuga bog'langan) ekanini bildiradi — CourseService.
// auditTopicLinks ("🔗 Havolalarni tekshirish") natijasi.
public record TopicLinkAuditItemDto(
        Long questionId,
        String questionTextSnippet,
        String actualHref,
        String expectedHref
) {
}
