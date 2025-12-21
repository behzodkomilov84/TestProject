package behzoddev.testproject.dao;

import behzoddev.testproject.dto.ScienceNameDto;
import behzoddev.testproject.entity.Science;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface ScienceRepository extends JpaRepository<Science, Long> {

    @EntityGraph(value = "scienceWithTopics")
    @Query("select s from Science s")
    Set<Science> findAllWithTopics();

    @EntityGraph(value = "scienceWithTopics")
    @Query("select s from Science s where s.id = :id")
    Optional<Science> findByIdWithTopics(Long id);


    @Query("select new behzoddev.testproject.dto.ScienceNameDto(s.id, s.name) from Science s")
    Set<ScienceNameDto> findAllScienceNames();

    @Query("select new behzoddev.testproject.dto.ScienceNameDto(s.id, s.name) from Science s where s.id = :id")
    Optional<ScienceNameDto> findScienceNameById(Long id);


}
