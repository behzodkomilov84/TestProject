package behzoddev.testproject.dao;

import behzoddev.testproject.dto.topic.TopicCourseTitleDto;
import behzoddev.testproject.entity.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Shu FANDAGI qaysi mavzular (Topic) biror kurs bo'limiga bog'langanini
    // BULK (bitta so'rov, N+1 emas) topish uchun — topics.html'da "🔗 Kurs:
    // ..." belgisini ko'rsatish (TopicService.getTopicsByScienceId).
    @Query("select new behzoddev.testproject.dto.topic.TopicCourseTitleDto(cs.linkedTopic.id, cs.course.title) " +
            "from CourseSection cs where cs.linkedTopic.science.id = :scienceId")
    List<TopicCourseTitleDto> findLinkedCourseTitlesByScienceId(@Param("scienceId") Long scienceId);

    // Bo'lim (CourseChapter) nomi o'zgartirilganda — shu Bo'limga tegishli,
    // TEST BOSHQARUVI'dagi Fan/Mavzuga bog'langan kurs mavzularini topish
    // uchun (CourseService.renameChapter -> syncTopicSectionNamesForChapter):
    // ularning TopicSection'i (agar hali eski nom bilan tursa) ham
    // shu YANGI nomga ko'chiriladi — bitta joyda o'zgartirilgan Bo'lim nomi
    // ikkala tomonda (kurs VA test boshqaruvi) sinxron qolishi uchun.
    List<CourseSection> findByChapter_IdAndLinkedTopicIsNotNull(Long chapterId);
}
