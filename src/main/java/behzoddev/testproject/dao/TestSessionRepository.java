package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TestSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestSessionRepository extends JpaRepository<TestSession, Long> {

    Page<TestSession> findByUserId(Long userId, Pageable pageable);

    List<TestSession> findByUserId(Long userId);

    Optional<TestSession> findByIdAndUserId(Long id, Long userId);
}

