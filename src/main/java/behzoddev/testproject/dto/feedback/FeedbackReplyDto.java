package behzoddev.testproject.dto.feedback;

import java.time.LocalDateTime;

public record FeedbackReplyDto(
        Long id,
        String replyText,
        Long authorId,
        String authorName,
        boolean authorIsStaff,
        LocalDateTime createdAt
) {
}
