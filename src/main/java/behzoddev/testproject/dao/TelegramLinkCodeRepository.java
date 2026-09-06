package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TelegramLinkCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, Long> {

    Optional<TelegramLinkCode> findByCodeAndUsedFalseAndCreatedAtAfter(
            String code,
            LocalDateTime time
    );

    Optional<TelegramLinkCode> findByCodeAndUsedFalse(String code);

    // Foydalanuvchini o'chirishdan OLDIN (UserServiceImpl#deleteUser) —
    // "user_id" FK RESTRICT (notifications/role_audit_logs'dagi bilan
    // bir xil muammo, 2026-09-06).
    void deleteByUser_Id(Long userId);
}
