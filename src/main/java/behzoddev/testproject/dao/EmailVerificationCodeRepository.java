package behzoddev.testproject.dao;

import behzoddev.testproject.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(
            String username, String code, LocalDateTime now);
}
