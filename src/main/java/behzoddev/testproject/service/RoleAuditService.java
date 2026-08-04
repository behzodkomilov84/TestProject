package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleAuditLogRepository;
import behzoddev.testproject.dto.audit.RoleAuditLogDto;
import behzoddev.testproject.entity.RoleAuditLog;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.RoleAuditAction;
import behzoddev.testproject.entity.enums.RoleAuditSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Rol o'zgarishlari audit tarixi — kim, qachon, kimga qanday rol
// berdi/olib tashladi (checkbox orqali qo'lda ham, Subscription orqali
// avtomatik ham).
@Service
@RequiredArgsConstructor
public class RoleAuditService {

    private final RoleAuditLogRepository roleAuditLogRepository;

    @Transactional
    public void record(User targetUser, User changedBy, String roleName,
                        RoleAuditAction action, RoleAuditSource source) {
        roleAuditLogRepository.save(RoleAuditLog.builder()
                .targetUser(targetUser)
                .changedBy(changedBy)
                .roleName(roleName)
                .action(action)
                .source(source)
                .build());
    }

    @Transactional(readOnly = true)
    public List<RoleAuditLogDto> listRecent() {
        return roleAuditLogRepository.findTop200ByOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleAuditLogDto> listForUser(Long userId) {
        return roleAuditLogRepository.findByTargetUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDto).toList();
    }

    private RoleAuditLogDto toDto(RoleAuditLog log) {
        return RoleAuditLogDto.builder()
                .id(log.getId())
                .targetUserId(log.getTargetUser().getId())
                .targetUsername(log.getTargetUser().getUsername())
                .changedById(log.getChangedBy() != null ? log.getChangedBy().getId() : null)
                .changedByUsername(log.getChangedBy() != null ? log.getChangedBy().getUsername() : "Tizim (avtomatik)")
                .roleName(log.getRoleName())
                .action(log.getAction().name())
                .source(log.getSource().name())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
