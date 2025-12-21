package behzoddev.testproject.dao;

import behzoddev.testproject.dto.TopicNameDto;
import behzoddev.testproject.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    @Query("select new behzoddev.testproject.dto.TopicNameDto(t.id, t.name) from Topic t where t.science.id = :id")
    Set<TopicNameDto> findTopicsByScienceId(Long id);

    @Query("select new behzoddev.testproject.dto.TopicNameDto(t.id, t.name) from Topic t where t.science.id = :scinceId and t.id = :topicId")
    TopicNameDto findTopicByIds(Long scinceId, Long topicId);
}
