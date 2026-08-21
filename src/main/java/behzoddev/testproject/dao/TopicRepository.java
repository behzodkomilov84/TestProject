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

    // ORDER BY t.id — aks holda mavzular tartibi bazaning ichki
    // qidiruv rejasiga bog'liq bo'lib qolardi (haqiqiy production bug:
    // Kimyo fanining 1-45 tartibli mavzulari aralashib chiqib qolgan edi).
    @Query("select new behzoddev.testproject.dto.topic.TopicIdAndNameDto(t.id, t.name) from Topic t where t.science.id = :id order by t.id")
    List<TopicIdAndNameDto> findTopicsByScienceId(@Param("id") Long id);

    @Query("select new behzoddev.testproject.dto.topic.TopicIdAndNameDto(t.id, t.name) from Topic t where t.science.id = :scienceId and t.id = :topicId")
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

    @Query("select new behzoddev.testproject.dto.topic.TopicWithQuestionCountDto(t.id, t.name, count(q.id)) FROM Topic t LEFT JOIN Question q ON q.topic.id = t.id WHERE t.science.id = :scienceId GROUP BY t.id, t.name ORDER BY t.name")
    List<TopicWithQuestionCountDto> getTopicsWithQuestionCount(@Param("scienceId") Long scienceId);
}
