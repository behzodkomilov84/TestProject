package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TestSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestSessionRepository extends JpaRepository<TestSession, Long> {

    Page<TestSession> findByUserId(
            Long userId,
            Pageable pageable
    );

    Optional<TestSession> findByIdAndUserId(Long id, Long userId);
}

