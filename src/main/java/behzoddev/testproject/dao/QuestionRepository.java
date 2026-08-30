package behzoddev.testproject.dao;

import behzoddev.testproject.dto.question.QuestionScienceTrashDto;
import behzoddev.testproject.dto.question.QuestionTrashDto;
import behzoddev.testproject.dto.question.TopicQuestionCountDto;
import behzoddev.testproject.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// DIQQAT: Question'da "O'chirilganlar savati" (deletedAt) bor — quyidagi
// o'quvchi/admin-facing "o'qish" metodlarining barchasi ATAYLAB "deletedAt
// is null" filtri bilan yozilgan (deleteByTopic_Id/softDeleteByTopic_Id'dan
// tashqari — ular ANIQ cascade-o'chirish/tiklash uchun).
public interface QuestionRepository extends JpaRepository<Question, Long> {

    // FAQAT TopicService.permanentlyDeleteTopic (mavzuni BUTUNLAY, qaytarib
    // bo'lmaydigan tarzda o'chirish) uchun — "questions.topic_id" ustunida
    // FK cheklovi YO'Q (init.sql), shu sabab bu yerda ANIQ chaqirilishi
    // kerak, aks holda "egasiz" savollar to'planib qoladi.
    void deleteByTopic_Id(Long topicId);

    // CourseService.deleteChapterWithLinkedTopics uchun — mavzu bilan birga
    // savollari ham SOFT-delete qilinadi (Question o'zining "O'chirilganlar
    // savati"dan alohida tiklanishi mumkin bo'lib qoladi).
    @Modifying
    @Query("update Question q set q.deletedAt = CURRENT_TIMESTAMP where q.topic.id = :topicId and q.deletedAt is null")
    void softDeleteByTopic_Id(@Param("topicId") Long topicId);

    @EntityGraph(value = "questionWithAnswers")
    @Query("""
                select distinct q
                from Question q
                where q.topic.science.id = :scienceId
                  and q.topic.id = :topicId
                  and q.deletedAt is null
            """)
    List<Question> getQuestionsByIds(@Param("scienceId") Long scienceId, @Param("topicId") Long topicId);

    @Query("""
            select q from Question q where q.topic.id = :topicId and q.deletedAt is null
            """)
    List<Question> getQuestionsByTopicId(@Param("topicId") Long topicId);

    @Query("""
            select q from Question q where q.id = :questionId and q.deletedAt is null
            """)
    Question getQuestionById(@Param("questionId") Long questionId);

    @Query("""
             select distinct q
             from Question q
             left join fetch q.answers
             where q.topic.id in :topicIds
               and q.deletedAt is null
            """)
    List<Question> findRandomQuestionsByTopicIds(@Param("topicIds") List<Long> topicIds);

    @Query("""
            SELECT count(q) FROM Question q
            WHERE q.topic.id IN :topicIds
              AND q.deletedAt is null
            """)
    int countByTopicIds(@Param("topicIds") List<Long> topicIds);

    // Kurs sahifasida ("Mavzu kartochkasi") har bir bog'langan mavzuning
    // ALOHIDA-ALOHIDA nechta faol savoli borligini BULK (bitta so'rov,
    // N+1 emas) ko'rsatish uchun — countByTopicIds'dan farqli, bu yerda
    // BARCHASI birga emas, har bir topicId uchun alohida son kerak
    // (CourseService.getDetail).
    @Query("select new behzoddev.testproject.dto.question.TopicQuestionCountDto(q.topic.id, count(q)) " +
            "from Question q where q.topic.id in :topicIds and q.deletedAt is null group by q.topic.id")
    List<TopicQuestionCountDto> countByTopicIdsGrouped(@Param("topicIds") List<Long> topicIds);

    @Query("""
            select q from Question q
            where q.topic.id = :topicId and q.deletedAt is null
            order by q.orderIndex
            """)
    Page<Question> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    @Query("""
                select q
                from Question q
                where q.topic.id = :topicId
                  and q.deletedAt is null
                  and (:search is null or lower(q.questionText) like lower(concat('%', :search, '%')))
                order by q.orderIndex
            """)
    Page<Question> findByTopicIdAndQuestionTextContainingIgnoreCase(Long topicId, String search, Pageable pageable);

    // ===== ALL MODE =====
    // ⬆⬇ / A-Z / Z-A saralash faqat shu ("Hammasi" — isAllMode) rejimda
    // ishlaydi (question.js), shu sabab aynan shu ikkita so'rov
    // ORDER BY orderIndex bilan yozilgan.

    @EntityGraph(attributePaths = "answers")
    List<Question> findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(Long topicId);

    @EntityGraph(attributePaths = "answers")
    List<Question> findByTopicIdAndQuestionTextContainingIgnoreCaseAndDeletedAtIsNullOrderByOrderIndexAsc(Long topicId, String questionText);

    // Reorder (⬆⬇, A-Z/Z-A) uchun — QuestionService.reorderQuestions.
    // MUHIM: yuqoridagi findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc
    // ATAYLAB ishlatilMAYDI — uning @EntityGraph(attributePaths="answers")
    // JOIN FETCH'i DISTINCT'siz bo'lgani uchun har bir savol o'zining
    // javoblari soncha marta takrorlanib qaytadi (masalan 5 ta javobli
    // savol — 5 marta), natijada ro'yxat hajmi frontend yuborgan id
    // to'plamidan har doim KATTA chiqib, "Savollar ro'yxati mavzuning
    // savollariga mos kelmayapti" xatosini bergan (haqiqiy production bug).
    // Bu yerda javoblar umuman kerak emas (faqat orderIndex o'zgaradi),
    // shu sabab oddiy (fetch join'siz) so'rov ishlatiladi.
    @Query("select q from Question q where q.topic.id = :topicId and q.deletedAt is null order by q.orderIndex")
    List<Question> findActiveByTopicIdOrderByOrderIndex(@Param("topicId") Long topicId);

    @Query("select max(q.orderIndex) from Question q where q.topic.id = :topicId")
    Integer findMaxOrderIndexByTopicId(@Param("topicId") Long topicId);

    @Query("""
                SELECT q
                FROM Question q
                LEFT JOIN UserQuestionStats s
                    ON q.id = s.id.questionId
                    AND s.id.userId = :userId
                WHERE q.topic.id IN :topicIds
                AND q.deletedAt is null
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

    // "O'chirilganlar savati" ro'yxati (mavzu ichida) — QuestionService.getDeletedQuestions.
    @Query("""
            select new behzoddev.testproject.dto.question.QuestionTrashDto(q.id, q.questionText, q.deletedAt)
            from Question q where q.topic.id = :topicId and q.deletedAt is not null order by q.deletedAt desc
            """)
    List<QuestionTrashDto> findDeletedByTopic_Id(@Param("topicId") Long topicId);

    // "O'chirilganlar savati" — BUTUN FAN bo'yicha (barcha mavzular birga,
    // topics.html'dagi global savol savati) — QuestionService.
    // getDeletedQuestionsByScience. LEFT JOIN t.section — mavzu bo'limsiz
    // bo'lsa ham qator natijadan tushib qolmasin uchun (xuddi
    // TopicRepository'dagi kabi).
    @Query("""
            select new behzoddev.testproject.dto.question.QuestionScienceTrashDto(
                q.id, q.questionText, q.deletedAt, t.id, t.name, s.name)
            from Question q
            join q.topic t
            left join t.section s
            where t.science.id = :scienceId and q.deletedAt is not null
            order by q.deletedAt desc
            """)
    List<QuestionScienceTrashDto> findDeletedByScienceId(@Param("scienceId") Long scienceId);
  }
