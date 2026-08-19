package behzoddev.testproject.telegram.dao;

import behzoddev.testproject.telegram.entity.TelegramSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TelegramSessionRepository extends JpaRepository<TelegramSession, Long> {

    // TelegramPracticeTestTimeoutService — Exam/Hard rejimida vaqti tugagan
    // testlarni avtomatik yakunlash uchun, hozir "IN_PRACTICE_TEST" holatidagi
    // barcha suhbatlarni topadi.
    List<TelegramSession> findByState(String state);
}
