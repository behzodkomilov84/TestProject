package behzoddev.testproject.dto.course;

// Savollar ro'yxatida "N ta javob" belgisini BULK (bitta so'rov, N+1
// emas) hisoblash uchun (CourseForumReplyRepository#countGroupedByThreadIds
// -> CourseForumService#listThreads).
public record ForumThreadReplyCountDto(Long threadId, long count) {
}
