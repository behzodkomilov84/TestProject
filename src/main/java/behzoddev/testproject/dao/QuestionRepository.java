package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select q from Question q
            where q.topic.id = :topicId
            """
    )
    Page<Question> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    @Query("""
                select q
                from Question q
                where q.topic.id = :topicId
                  and (:search is null or lower(q.questionText) like lower(concat('%', :search, '%')))
            """)
    Page<Question> findByTopicIdAndQuestionTextContainingIgnoreCase(
            Long topicId,
            String search,
            Pageable pageable
    );

    // ===== ALL MODE =====

    @EntityGraph(attributePaths = "answers")
    List<Question> findByTopicId(Long topicId);

    @EntityGraph(attributePaths = "answers")
    List<Question> findByTopicIdAndQuestionTextContainingIgnoreCase(
            Long topicId,
            String questionText
    );

    @Query("""
    SELECT q
    FROM Question q
    LEFT JOIN UserQuestionStats s
        ON q.id = s.id.questionId
        AND s.id.userId = :userId
    WHERE q.topic.id IN :topicIds
    AND (
        s IS NULL
        OR (
            s.totalAttempts > 0
            AND (s.correctAttempts * 1.0 / s.totalAttempts) < 0.8
        )
    )
    ORDER BY
        COALESCE(
            1.0 - (
                s.correctAttempts * 1.0 /
                CASE
                    WHEN s.totalAttempts = 0 OR s.totalAttempts IS NULL
                    THEN 1
                    ELSE s.totalAttempts
                END
            ),
            0.7
        ) DESC
""")
    List<Question> findHardForUser(Long userId, List<Long> topicIds);

}
