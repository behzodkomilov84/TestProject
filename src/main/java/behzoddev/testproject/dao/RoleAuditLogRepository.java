package behzoddev.testproject.dao;

import behzoddev.testproject.entity.RoleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAuditLogRepository extends JpaRepository<RoleAuditLog, Long> {

    List<RoleAuditLog> findTop200ByOrderByCreatedAtDesc();

    List<RoleAuditLog> findByTargetUser_IdOrderByCreatedAtDesc(Long targetUserId);
}
