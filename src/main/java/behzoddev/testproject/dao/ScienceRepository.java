package behzoddev.testproject.dao;

import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.entity.Science;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

public interface ScienceRepository extends JpaRepository<Science, Long> {

    @EntityGraph(value = "scienceWithTopics")
    @Query("select s from Science s")
    Set<Science> findAllWithTopics();

    @EntityGraph(value = "scienceWithTopics")
    @Query("select s from Science s where s.id = :id")
    Optional<Science> findByIdWithTopics(Long id);


    @Query("select new behzoddev.testproject.dto.science.ScienceIdAndNameDto(s.id, s.name) from Science s")
    Set<ScienceIdAndNameDto> findAllScienceNames();

    @Query("select new behzoddev.testproject.dto.science.ScienceIdAndNameDto(s.id, s.name) from Science s where s.id = :id")
    Optional<ScienceIdAndNameDto> findScienceNameById(@Param("id") Long id);

    @Query("select s from Science s where s.name = :name")
    Optional<Science> findByName(@Param("name") String name);

    @Query("UPDATE Science s set s.name=:name where s.id=:id")
    @Modifying
    void updateScienceName(@Param("id") Long id, @Param("name") String name);

    @Transactional
    boolean existsByName(String name);
}
