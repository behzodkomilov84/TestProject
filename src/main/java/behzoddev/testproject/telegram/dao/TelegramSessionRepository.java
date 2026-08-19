package behzoddev.testproject.telegram.dao;

import behzoddev.testproject.telegram.entity.TelegramSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramSessionRepository extends JpaRepository<TelegramSession, Long> {
}
