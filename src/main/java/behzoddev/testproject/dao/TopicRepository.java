package behzoddev.testproject.dao;

import behzoddev.testproject.dto.topic.TopicIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    // ORDER BY t.orderIndex — aniq tartib maydoni (ilgari "ORDER BY t.id"
    // workaround ishlatilgan edi, haqiqiy production bug: Kimyo fanining
    // 1-45 tartibli mavzulari aralashib chiqib qolgan edi).
    // LEFT JOIN t.section — MUHIM: oddiy "t.section.id" implicit INNER
    // JOIN hosil qilib, section=NULL bo'lgan mavzularni natijadan
    // butunlay chiqarib tashlashi mumkin edi (aynan shu xato turi
    // yuqoridagi t.id workaround'iga sabab bo'lgan edi) — shu sabab
    // ataylab aniq LEFT JOIN ishlatilgan.
    @Query("select new behzoddev.testproject.dto.topic.TopicIdAndNameDto(t.id, t.name, s.id) " +
            "from Topic t LEFT JOIN t.section s where t.science.id = :id order by t.orderIndex")
    List<TopicIdAndNameDto> findTopicsByScienceId(@Param("id") Long id);

    @Query("select new behzoddev.testproject.dto.topic.TopicIdAndNameDto(t.id, t.name, s.id) " +
            "from Topic t LEFT JOIN t.section s where t.science.id = :scienceId and t.id = :topicId")
    TopicIdAndNameDto findTopicByIds(@Param("scienceId") Long scienceId, @Param("topicId") Long topicId);

    // Kurs bo'limini TEST BOSHQARUVI'dagi mavzuga bog'lashda — mavjud
    // mavzuni topish uchun (CourseService.resolveLinkedTopic).
    Optional<Topic> findByScience_IdAndName(Long scienceId, String name);

    @Query("select t.science.id from Topic t where t.id = :topicId")
    Long getScienceIdByTopicId(@Param("topicId") Long topicId);

    @Query("UPDATE Topic t set t.name=:newName where t.id=:id")
    @Modifying
    void updateTopicName(@Param("id") Long id, @Param("newName") String newName);

    @Query("select t from Topic t where t.id = :id")
    Topic getTopicById(@Param("id") Long id);

    // Bo'limi bor mavzular avval (bo'lim tartibi, keyin mavzu tartibi
    // bo'yicha), bo'limsizlar oxirida (hozirgidek, testConfigPage.js'da
    // sarlavhasiz/tekis ro'yxat sifatida ko'rsatiladi).
    @Query("select new behzoddev.testproject.dto.topic.TopicWithQuestionCountDto(" +
            "t.id, t.name, count(q.id), s.id, s.name, s.orderIndex) " +
            "FROM Topic t LEFT JOIN Question q ON q.topic.id = t.id LEFT JOIN t.section s " +
            "WHERE t.science.id = :scienceId " +
            "GROUP BY t.id, t.name, t.orderIndex, s.id, s.name, s.orderIndex " +
            "ORDER BY CASE WHEN s.id IS NULL THEN 1 ELSE 0 END, s.orderIndex, t.orderIndex")
    List<TopicWithQuestionCountDto> getTopicsWithQuestionCount(@Param("scienceId") Long scienceId);

    List<Topic> findBySection_IdOrderByOrderIndexAsc(Long sectionId);

    List<Topic> findByScience_IdAndSectionIsNullOrderByOrderIndexAsc(Long scienceId);

    @Query("select max(t.orderIndex) from Topic t where t.science.id = :scienceId")
    Integer findMaxOrderIndexByScienceId(@Param("scienceId") Long scienceId);
}
