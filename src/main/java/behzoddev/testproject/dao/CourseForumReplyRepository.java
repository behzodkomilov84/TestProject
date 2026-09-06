package behzoddev.testproject.dao;

import behzoddev.testproject.dto.course.ForumThreadReplyCountDto;
import behzoddev.testproject.entity.CourseForumReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseForumReplyRepository extends JpaRepository<CourseForumReply, Long> {

    // Bitta savolning javoblari — eng eskisi tepada (suhbat tartibida,
    // CourseForumService#listReplies).
    List<CourseForumReply> findByThread_IdAndDeletedAtIsNullOrderByCreatedAtAsc(Long threadId);

    // Savollar ro'yxatida "N ta javob" belgisini BULK hisoblash uchun
    // (bitta so'rov, N+1 emas — CourseForumService#listThreads).
    @Query("""
            select new behzoddev.testproject.dto.course.ForumThreadReplyCountDto(r.thread.id, count(r))
            from CourseForumReply r
            where r.thread.id in :threadIds and r.deletedAt is null
            group by r.thread.id
            """)
    List<ForumThreadReplyCountDto> countGroupedByThreadIds(@Param("threadIds") List<Long> threadIds);
}
