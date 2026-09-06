package behzoddev.testproject.dao;

import behzoddev.testproject.entity.RoleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleAuditLogRepository extends JpaRepository<RoleAuditLog, Long> {

    List<RoleAuditLog> findTop200ByOrderByCreatedAtDesc();

    List<RoleAuditLog> findByTargetUser_IdOrderByCreatedAtDesc(Long targetUserId);

    // Foydalanuvchini o'chirishdan OLDIN (UserServiceImpl#deleteUser) —
    // "role_audit_logs.target_user_id" FK RESTRICT (haqiqiy topilgan bug,
    // 2026-09-06 — notifications'dan keyin topilgan XUDDI SHUNDAY muammo).
    void deleteByTargetUser_Id(Long targetUserId);

    // "changed_by_id" — AVVAL ATAYLAB tegilmagan edi ("boshqa foydalanuvchi
    // haqidagi tarixiy yozuv yo'qolib qolmasin" deb), lekin bu ustun ham
    // NOT NULL EMAS FK RESTRICT ekan (haqiqiy topilgan bug, 2026-09-07:
    // agar o'chirilayotgan foydalanuvchi ilgari BOSHQA birovning rolini
    // o'zgartirgan bo'lsa, o'sha eski yozuv uni o'chirishga to'sqinlik
    // qilardi). Qator o'ZI o'chirilmaydi (target/rol/vaqt saqlanib
    // qoladi) — faqat "kim o'zgartirgani" NULL qilinadi.
    @Modifying
    @Query("UPDATE RoleAuditLog r SET r.changedBy = NULL WHERE r.changedBy.id = :userId")
    void clearChangedBy(@Param("userId") Long userId);
}
