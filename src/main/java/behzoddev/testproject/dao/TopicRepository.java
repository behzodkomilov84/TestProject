package behzoddev.testproject.dao;

import behzoddev.testproject.dto.TopicIdAndNameDto;
import behzoddev.testproject.dto.TopicShortDto;
import behzoddev.testproject.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Query("select new behzoddev.testproject.dto.TopicIdAndNameDto(t.id, t.name) from Topic t where t.science.id = :id")
    Set<TopicIdAndNameDto> findTopicsByScienceId(@Param("id") Long id);

    @Query("select new behzoddev.testproject.dto.TopicIdAndNameDto(t.id, t.name) from Topic t where t.science.id = :scienceId and t.id = :topicId")
    TopicIdAndNameDto findTopicByIds(@Param("scienceId") Long scienceId, @Param("topicId") Long topicId);

    @Query("select t.science.id from Topic t where t.id = :topicId")
    Long getScienceIdByTopicId(@Param("topicId") Long topicId);

    @Query("select new behzoddev.testproject.dto.TopicShortDto(t.id, t.name, t.science.id) from Topic t where t.science.id = :id")
    Set<TopicShortDto> getTopicShortDtoByScienceId(@Param("scienceId") Long scienceId);

    @Query("UPDATE Topic t set t.name=:newName where t.id=:id")
    @Modifying
    void updateTopicName(@Param("id") Long id,@Param("newName") String newName);

}
