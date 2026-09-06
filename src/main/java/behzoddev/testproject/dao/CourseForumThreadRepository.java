package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseForumThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseForumThreadRepository extends JpaRepository<CourseForumThread, Long> {

    // "Forum" savollari ro'yxati — eng yangisi tepada (CourseForumService#listThreads).
    List<CourseForumThread> findByCourse_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long courseId);
}
