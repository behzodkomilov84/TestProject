package behzoddev.testproject.dto.audit;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record RoleAuditLogDto(
        Long id,
        Long targetUserId,
        String targetUsername,
        Long changedById,
        String changedByUsername,
        String roleName,
        String action,
        String source,
        LocalDateTime createdAt
) {
}
