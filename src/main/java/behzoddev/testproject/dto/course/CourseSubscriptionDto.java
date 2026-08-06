package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record CourseSubscriptionDto(
        Long id,
        Long userId,
        String username,
        Long courseId,
        String courseTitle,
        BigDecimal amount,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String note,
        LocalDateTime createdAt
) {
}
