package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
