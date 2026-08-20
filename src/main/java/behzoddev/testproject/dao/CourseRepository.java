package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByPublishedTrueOrderByCreatedAtDesc();

    List<Course> findAllByOrderByCreatedAtDesc();

    // ADMIN uchun katalog — chop etilgan barcha kurslar + o'zi yaratgan
    // qoralamalar ham (aks holda ADMIN o'zi hali chop etmagan kursini
    // katalogda topa olmay qolardi).
    List<Course> findByPublishedTrueOrCreatedBy_IdOrderByCreatedAtDesc(Long createdById);
}
