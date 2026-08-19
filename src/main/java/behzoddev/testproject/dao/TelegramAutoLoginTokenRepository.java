package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TelegramAutoLoginToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramAutoLoginTokenRepository extends JpaRepository<TelegramAutoLoginToken, Long> {

    Optional<TelegramAutoLoginToken> findByTokenAndUsedFalse(String token);
}
