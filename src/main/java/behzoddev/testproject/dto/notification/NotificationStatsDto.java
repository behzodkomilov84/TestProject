package behzoddev.testproject.dto.notification;

import lombok.Builder;

// Bildirishnomalar sahifasidagi KPI kartochkalari uchun.
@Builder
public record NotificationStatsDto(long total, long unread, long read) {
}
