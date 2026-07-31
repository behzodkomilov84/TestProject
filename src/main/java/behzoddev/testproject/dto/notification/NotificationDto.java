package behzoddev.testproject.dto.notification;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NotificationDto(Long id, String message, String link, boolean read, LocalDateTime createdAt) {
}
