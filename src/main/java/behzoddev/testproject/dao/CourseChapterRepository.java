package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseChapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseChapterRepository extends JpaRepository<CourseChapter, Long> {

    // Bo'lim nomi kiritilganda — mavjud bo'lsa shuni ishlatish (yangi
    // yaratmaslik), aks holda avtomatik yaratish uchun (CourseService.resolveChapter).
    Optional<CourseChapter> findByCourse_IdAndNameIgnoreCase(Long courseId, String name);

    // Yangi bo'lim doim oxiriga qo'shiladi.
    Optional<CourseChapter> findTopByCourse_IdOrderByOrderIndexDesc(Long courseId);

    // Kursni o'chirishdan oldin — course_chapters.course_id FK CASCADE
    // bo'lsa ham, boshqa CourseSection/Course o'chirish tartibi bilan bir
    // xil izchillik uchun aniq chaqiriladi (CourseService.deleteCourse).
    void deleteByCourse_Id(Long courseId);
}
