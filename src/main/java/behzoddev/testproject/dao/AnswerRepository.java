package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Integer> {

    @Query("""
            SELECT COUNT(a) > 0
            FROM Answer a
            WHERE a.id = :answerId
              AND a.question.id = :questionId
              AND a.isTrue = true
            """)
    boolean isCorrect(@Param("questionId") Long questionId,
                      @Param("answerId") Long answerId);

    @Query("""
                    from Answer a where a.id = :id
            """)
    Optional<Answer> findById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update Answer a set a.commentary = :commentary where a.id = :id
            """
    )
    int updateCommentOfTrueAnswer(@Param("id") Long id, @Param("commentary") String commentary);
}
