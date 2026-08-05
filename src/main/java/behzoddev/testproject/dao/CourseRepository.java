package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByPublishedTrueOrderByCreatedAtDesc();

    List<Course> findAllByOrderByCreatedAtDesc();
}
