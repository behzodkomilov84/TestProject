package behzoddev.testproject.dto.subscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionDto(
        Long id,
        Long userId,
        String username,
        BigDecimal amount,
        String source,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String note,
        LocalDateTime createdAt
) {
}
