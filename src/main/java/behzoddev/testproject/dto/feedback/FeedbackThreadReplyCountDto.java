package behzoddev.testproject.dto.feedback;

// Ro'yxatda "N ta javob" belgisini BULK (bitta so'rov, N+1 emas)
// hisoblash uchun (FeedbackReplyRepository#countGroupedByThreadIds ->
// FeedbackService#listThreads).
public record FeedbackThreadReplyCountDto(Long threadId, long count) {
}
