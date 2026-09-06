package behzoddev.testproject.dao;

import behzoddev.testproject.dto.feedback.FeedbackThreadReplyCountDto;
import behzoddev.testproject.entity.FeedbackReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackReplyRepository extends JpaRepository<FeedbackReply, Long> {

    // Bitta fikr-taklifning javoblari — eng eskisi tepada (suhbat
    // tartibida, FeedbackService#listReplies).
    List<FeedbackReply> findByThread_IdAndDeletedAtIsNullOrderByCreatedAtAsc(Long threadId);

    // Ro'yxatda "N ta javob" belgisini BULK hisoblash uchun (bitta so'rov,
    // N+1 emas — FeedbackService#listThreads).
    @Query("""
            select new behzoddev.testproject.dto.feedback.FeedbackThreadReplyCountDto(r.thread.id, count(r))
            from FeedbackReply r
            where r.thread.id in :threadIds and r.deletedAt is null
            group by r.thread.id
            """)
    List<FeedbackThreadReplyCountDto> countGroupedByThreadIds(@Param("threadIds") List<Long> threadIds);
}
