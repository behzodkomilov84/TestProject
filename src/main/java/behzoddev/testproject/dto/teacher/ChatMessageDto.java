package behzoddev.testproject.dto.teacher;

import java.time.LocalDateTime;

public record ChatMessageDto(
        Long id,
        Long senderId,
        String senderName,
        String message,
        String role,
        LocalDateTime createdAt
) {
}
