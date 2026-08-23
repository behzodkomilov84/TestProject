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

    // Kursni o'chirishdan oldin — foreign key RESTRICT bo'lgani uchun,
    // avval shu kursning barcha bo'limlarini o'chirish kerak
    // (CourseService.deleteCourse).
    void deleteByCourse_Id(Long courseId);

    // Berilgan mavzuga (Topic) bog'langan bo'lim — test yaratish formasida
    // "🔗 Mavzuga havola qo'shish" tugmasi shu orqali to'g'ri course/section
    // ID'larini topadi (TopicService.getCourseLinkForTopic).
    Optional<CourseSection> findByLinkedTopic_Id(Long topicId);
}
