package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.QuestionSetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionSetItemRepository extends JpaRepository<QuestionSetItem, Long> {

    @Query("""
                select q
                from QuestionSetItem i
                join i.question q
                left join fetch q.answers
                where i.questionSet.id = :setId
            """)
    List<Question> fetchQuestionsForSet(@Param("setId") Long setId);
}
