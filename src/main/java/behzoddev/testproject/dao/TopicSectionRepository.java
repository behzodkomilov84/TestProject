package behzoddev.testproject.dao;

import behzoddev.testproject.dto.section.TopicSectionIdAndNameDto;
import behzoddev.testproject.entity.TopicSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicSectionRepository extends JpaRepository<TopicSection, Long> {

    @Query("select new behzoddev.testproject.dto.section.TopicSectionIdAndNameDto(s.id, s.name, s.orderIndex) " +
            "from TopicSection s where s.science.id = :scienceId order by s.orderIndex")
    List<TopicSectionIdAndNameDto> findByScienceIdOrderByOrderIndex(@Param("scienceId") Long scienceId);

    List<TopicSection> findByScience_IdOrderByOrderIndexAsc(Long scienceId);

    boolean existsByScience_IdAndNameIgnoreCase(Long scienceId, String name);

    @Query("select max(s.orderIndex) from TopicSection s where s.science.id = :scienceId")
    Integer findMaxOrderIndexByScienceId(@Param("scienceId") Long scienceId);
}
