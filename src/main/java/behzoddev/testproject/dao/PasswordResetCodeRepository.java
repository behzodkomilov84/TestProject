package behzoddev.testproject.dao;

import behzoddev.testproject.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(
            String username,
            String code,
            LocalDateTime now
    );

    // Foydalanuvchini o'chirishdan OLDIN (UserServiceImpl#deleteUser) —
    // "user_id" FK RESTRICT (notifications/role_audit_logs'dagi bilan
    // bir xil muammo, 2026-09-06).
    void deleteByUser_Id(Long userId);
}
