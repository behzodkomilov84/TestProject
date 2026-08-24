package behzoddev.testproject.dao;

import behzoddev.testproject.dto.course.CourseChapterDto;
import behzoddev.testproject.entity.CourseChapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseChapterRepository extends JpaRepository<CourseChapter, Long> {

    // Bo'lim nomi kiritilganda — mavjud bo'lsa shuni ishlatish (yangi
    // yaratmaslik), aks holda avtomatik yaratish uchun (CourseService.resolveChapter).
    Optional<CourseChapter> findByCourse_IdAndNameIgnoreCase(Long courseId, String name);

    // Yangi bo'lim doim oxiriga qo'shiladi.
    Optional<CourseChapter> findTopByCourse_IdOrderByOrderIndexDesc(Long courseId);

    // Kursning BARCHA Bo'limlari — hozircha hech qanday mavzuga
    // biriktirilmagan (sectionCount=0, "bo'sh") bo'lganlari ham shu
    // jumladan (courseDetail.js Bo'lim tanlash select'ini to'liq
    // to'ldirish + bo'sh bo'limlarni o'chirish imkoniyati uchun —
    // CourseController.getChapters/deleteChapter). Korrelyatsiyalangan
    // subso'rov — count() doim aniq bitta qatorli, fan-out xavfi yo'q.
    @Query("select new behzoddev.testproject.dto.course.CourseChapterDto(c.id, c.name, c.orderIndex, " +
            "(select count(s) from CourseSection s where s.chapter = c)) " +
            "from CourseChapter c where c.course.id = :courseId order by c.orderIndex")
    List<CourseChapterDto> findByCourseIdWithSectionCount(@Param("courseId") Long courseId);

    // Kursni o'chirishdan oldin — course_chapters.course_id FK CASCADE
    // bo'lsa ham, boshqa CourseSection/Course o'chirish tartibi bilan bir
    // xil izchillik uchun aniq chaqiriladi (CourseService.deleteCourse).
    void deleteByCourse_Id(Long courseId);
}
