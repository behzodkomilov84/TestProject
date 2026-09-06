package behzoddev.testproject.dao;

import behzoddev.testproject.entity.RoleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAuditLogRepository extends JpaRepository<RoleAuditLog, Long> {

    List<RoleAuditLog> findTop200ByOrderByCreatedAtDesc();

    List<RoleAuditLog> findByTargetUser_IdOrderByCreatedAtDesc(Long targetUserId);

    // Foydalanuvchini o'chirishdan OLDIN (UserServiceImpl#deleteUser) —
    // "role_audit_logs.target_user_id" FK RESTRICT (haqiqiy topilgan bug,
    // 2026-09-06 — notifications'dan keyin topilgan XUDDI SHUNDAY muammo).
    // "changed_by_id" ATAYLAB tegilmaydi — bu foydalanuvchi BOSHQA
    // birovning rolini o'zgartirgan bo'lsa, o'sha tarixiy yozuv (boshqa
    // maqsadli foydalanuvchi haqida) yo'qolib qolmasin deb.
    void deleteByTargetUser_Id(Long targetUserId);
}
