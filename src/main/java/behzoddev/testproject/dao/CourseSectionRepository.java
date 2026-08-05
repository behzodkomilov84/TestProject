package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {

    List<CourseSection> findByCourse_IdOrderByOrderIndexAsc(Long courseId);

    Optional<CourseSection> findByCourse_IdAndOrderIndex(Long courseId, int orderIndex);

    long countByCourse_Id(Long courseId);

    // Kursning eng katta tartib raqami — yangi bo'lim qo'shishda "oxiriga qo'shish" uchun.
    Optional<CourseSection> findTopByCourse_IdOrderByOrderIndexDesc(Long courseId);
}
