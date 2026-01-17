package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Question;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(value = "questionWithAnswers")
    @Query("""
                select distinct q
                from Question q
                where q.topic.science.id = :scienceId
                  and q.topic.id = :topicId
            """)
    List<Question> getQuestionsByIds(
            @Param("scienceId") Long scienceId,
            @Param("topicId") Long topicId
    );

    @Query("""
            select q from Question q where q.topic.id = :topicId
            """)
    List<Question> getQuestionsByTopicId(
            @Param("topicId") Long topicId);

    @Query("""
            select q from Question q where q.id = :questionId
            """)
    Question getQuestionById(
            @Param("questionId") Long questionId);

    @Query("""
             select distinct q
             from Question q
             left join fetch q.answers
             where q.topic.id in :topicIds
            """)
    List<Question> findRandomQuestionsByTopicIds(@Param("topicIds") List<Long> topicIds);

    @Query("""
            SELECT count(q) FROM Question q
            WHERE q.topic.id IN :topicIds
            """)
    int countByTopicIds(@Param("topicIds") List<Long> topicIds);

}
