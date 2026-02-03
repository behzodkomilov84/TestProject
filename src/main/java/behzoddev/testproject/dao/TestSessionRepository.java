package behzoddev.testproject.dao;

import behzoddev.testproject.dto.TestSessionHistoryDto;
import behzoddev.testproject.entity.TestSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestSessionRepository extends JpaRepository<TestSession, Long> {


    @Query("""
            select new behzoddev.testproject.dto.TestSessionHistoryDto
            (
            t.id,
            s.name,
            t.totalQuestions,
            t.correctAnswers,
            t.percent,
            t.finishedAt,
            t.durationSec
            )
            from TestSession t
            join t.questions q
            join q.question qu
            join qu.topic tp
            join tp.science s
            where t.user.id = :userId
            and t.finishedAt is not null
            group by t.id, s.name, t.finishedAt,
            t.totalQuestions, t.correctAnswers,
            t.percent, t.durationSec
            order by t.finishedAt desc
            """)
    Page<TestSessionHistoryDto> findByUserId(@Param("userId") Long userId, Pageable pageable);

    List<TestSession> findByUserId(Long userId);

    Optional<TestSession> findByIdAndUserId(Long id, Long userId);

}

