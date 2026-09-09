package behzoddev.testproject.dto.user;

import lombok.Builder;

import java.util.Set;

// "/users" sahifasidagi "🟢 Onlayn" belgisi va statistika uchun
// (foydalanuvchi so'rovi, 2026-09-09) — OnlineUserTracker'dan.
@Builder
public record OnlineStatusDto(
        Set<Long> onlineUserIds,
        long onlineCount
) {
}
