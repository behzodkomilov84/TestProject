package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseSectionProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseSectionProgressRepository extends JpaRepository<CourseSectionProgress, Long> {

    boolean existsByUser_IdAndSection_Id(Long userId, Long sectionId);

    List<CourseSectionProgress> findByUser_IdAndSection_Course_Id(Long userId, Long courseId);
}
