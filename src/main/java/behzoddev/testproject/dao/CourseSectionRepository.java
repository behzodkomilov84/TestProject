package behzoddev.testproject.dao;

import behzoddev.testproject.dto.course.CourseSectionTrashDto;
import behzoddev.testproject.dto.course.TopicExplanationSearchResultDto;
import behzoddev.testproject.dto.section.TopicSectionCourseTitleDto;
import behzoddev.testproject.dto.topic.TopicCourseTitleDto;
import behzoddev.testproject.entity.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// DIQQAT: CourseSection'da "O'chirilganlar savati" (deletedAt) bor —
// quyidagi metodlarning DEYARLI barchasi (permanentlyDeleteCourse'da
// ishlatiladigan deleteByCourse_Id'dan tashqari) ATAYLAB "deletedAt is
// null" filtri bilan yozilgan, chunki soft-delete qilingan bo'lim endi
// "yo'q" deb hisoblanishi kerak — kurs ko'rinishida, "🔗 Kurs" belgilarida,
// bo'sh-bo'lim tekshiruvida va h.k. Metod NOMLARI o'zgartirilmagan (faqat
// ichki JPQL'ga filtr qo'shilgan) — shu sabab CourseService'dagi chaqiruv
// joylarini yangilash shart emas.
public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {

    @Query("select cs from CourseSection cs where cs.course.id = :courseId and cs.deletedAt is null order by cs.orderIndex asc")
    List<CourseSection> findByCourse_IdOrderByOrderIndexAsc(@Param("courseId") Long courseId);

    @Query("select cs from CourseSection cs where cs.course.id = :courseId and cs.orderIndex = :orderIndex and cs.deletedAt is null")
    Optional<CourseSection> findByCourse_IdAndOrderIndex(@Param("courseId") Long courseId, @Param("orderIndex") int orderIndex);

    @Query("select count(cs) from CourseSection cs where cs.course.id = :courseId and cs.deletedAt is null")
    long countByCourse_Id(@Param("courseId") Long courseId);

    // Kursning eng katta tartib raqami — yangi bo'lim qo'shishda "oxiriga qo'shish" uchun.
    @Query("select cs from CourseSection cs where cs.course.id = :courseId and cs.deletedAt is null order by cs.orderIndex desc limit 1")
    Optional<CourseSection> findTopByCourse_IdOrderByOrderIndexDesc(@Param("courseId") Long courseId);

    // Kursni BUTUNLAY (permanentlyDeleteCourse) o'chirishdan oldin — foreign
    // key RESTRICT bo'lgani uchun, avval shu kursning BARCHA bo'limlarini
    // (soft-delete qilinganlari HAM) o'chirish kerak — shu sabab bu metod
    // ATAYLAB filtrlanmagan.
    void deleteByCourse_Id(Long courseId);

    // Berilgan mavzuga (Topic) bog'langan bo'lim — test yaratish formasida
    // "🔗 Mavzuga havola qo'shish" tugmasi shu orqali to'g'ri course/section
    // ID'larini topadi (TopicService.getCourseLinkForTopic).
    @Query("select cs from CourseSection cs where cs.linkedTopic.id = :topicId and cs.deletedAt is null")
    Optional<CourseSection> findByLinkedTopic_Id(@Param("topicId") Long topicId);

    // "🔗 Havolalarni tekshirish" — shu kursning TEST BOSHQARUVIga
    // bog'langan BARCHA bo'limlari (CourseService.auditTopicLinks).
    @Query("select cs from CourseSection cs where cs.course.id = :courseId and cs.linkedTopic is not null and cs.deletedAt is null")
    List<CourseSection> findByCourse_IdAndLinkedTopicIsNotNull(@Param("courseId") Long courseId);

    // Bitta aniq mavzu ANIQ shu kursga bog'langanmi (himoya tekshiruvi) —
    // CourseService.addMissingTopicLinks / fixWrongTopicLink.
    @Query("select cs from CourseSection cs where cs.course.id = :courseId and cs.linkedTopic.id = :topicId and cs.deletedAt is null")
    Optional<CourseSection> findByCourse_IdAndLinkedTopic_Id(@Param("courseId") Long courseId, @Param("topicId") Long topicId);

    // Shu FANDAGI qaysi mavzular (Topic) biror kurs bo'limiga bog'langanini
    // BULK (bitta so'rov, N+1 emas) topish uchun — topics.html'da "🔗 Kurs:
    // ..." belgisini ko'rsatish (TopicService.getTopicsByScienceId).
    @Query("select new behzoddev.testproject.dto.topic.TopicCourseTitleDto(cs.linkedTopic.id, cs.course.title) " +
            "from CourseSection cs where cs.linkedTopic.science.id = :scienceId and cs.deletedAt is null")
    List<TopicCourseTitleDto> findLinkedCourseTitlesByScienceId(@Param("scienceId") Long scienceId);

    // Bo'lim (CourseChapter) nomi o'zgartirilganda — shu Bo'limga tegishli,
    // TEST BOSHQARUVI'dagi Fan/Mavzuga bog'langan kurs mavzularini topish
    // uchun (CourseService.renameChapter -> syncTopicSectionNamesForChapter):
    // ularning TopicSection'i (agar hali eski nom bilan tursa) ham
    // shu YANGI nomga ko'chiriladi — bitta joyda o'zgartirilgan Bo'lim nomi
    // ikkala tomonda (kurs VA test boshqaruvi) sinxron qolishi uchun.
    @Query("select cs from CourseSection cs where cs.chapter.id = :chapterId and cs.linkedTopic is not null and cs.deletedAt is null")
    List<CourseSection> findByChapter_IdAndLinkedTopicIsNotNull(@Param("chapterId") Long chapterId);

    // Bo'sh (hech qanday mavzuga biriktirilmagan) Bo'limni o'chirish
    // xavfsizligini tekshirish uchun (CourseService.deleteChapter).
    @Query("select case when count(cs) > 0 then true else false end from CourseSection cs where cs.chapter.id = :chapterId and cs.deletedAt is null")
    boolean existsByChapter_Id(@Param("chapterId") Long chapterId);

    // Shu FANDAGI qaysi TEST BOSHQARUVI Bo'limlari (TopicSection) biror
    // kursga bog'langanini BULK topish uchun (TopicSectionService.
    // getSectionsByScienceId — "🔗 Kurs: ..." belgisi).
    @Query("select new behzoddev.testproject.dto.section.TopicSectionCourseTitleDto(cs.linkedTopic.section.id, cs.course.title) " +
            "from CourseSection cs where cs.linkedTopic.section is not null and cs.linkedTopic.section.science.id = :scienceId and cs.deletedAt is null")
    List<TopicSectionCourseTitleDto> findLinkedCourseTitlesBySectionScienceId(@Param("scienceId") Long scienceId);

    // Bitta aniq Bo'lim (TopicSection) biror kursga bog'langanmi — bog'lansa
    // qaysi kursgaligini bilish uchun (TopicSectionService.updateSectionName
    // — shu bo'limni TEST BOSHQARUVIDAN tahrirlashni bloklaydi, chunki nomi
    // kurs Bo'limi bilan bir tomonlama sinxronlangan — faqat kurs ichidan
    // o'zgartirilishi kerak).
    @Query("select cs from CourseSection cs where cs.linkedTopic.section.id = :sectionId and cs.deletedAt is null order by cs.id limit 1")
    Optional<CourseSection> findFirstByLinkedTopic_Section_Id(@Param("sectionId") Long sectionId);

    // "O'chirilganlar savati" ro'yxati (kurs ichida) — CourseService.getDeletedSections.
    @Query("select new behzoddev.testproject.dto.course.CourseSectionTrashDto(cs.id, cs.title, cs.deletedAt) " +
            "from CourseSection cs where cs.course.id = :courseId and cs.deletedAt is not null order by cs.deletedAt desc")
    List<CourseSectionTrashDto> findDeletedByCourse_Id(@Param("courseId") Long courseId);

    // "Kurs ichidan mavzu yoritmasi bo'yicha qidiruv" — 1-bosqich:
    // berilgan mavzular (topicIds — topics.html'dagi joriy sahifa yoki
    // courseDetail.js'dagi joriy kursning bog'langan mavzulari) qaysi
    // kurs(lar)ga bog'langanini aniqlaydi (CourseService.searchTopicExplanations).
    @Query("select distinct cs.course.id from CourseSection cs where cs.linkedTopic.id in :topicIds and cs.deletedAt is null")
    List<Long> findCourseIdsByLinkedTopicIds(@Param("topicIds") List<Long> topicIds);

    // 2-bosqich: shu kurs(lar)ning BARCHA (istalgan Bo'lim/chapter'dagi,
    // biror mavzuga bog'langan) bo'limlari orasidan matn darsi
    // (textContent — "mavzu yoritmasi") ichida qidiruv so'zi bor
    // bo'limlarni topadi.
    @Query("""
            select new behzoddev.testproject.dto.course.TopicExplanationSearchResultDto(
                cs.linkedTopic.id, cs.linkedTopic.name, cs.course.id, cs.course.title, cs.id, cs.title)
            from CourseSection cs
            where cs.course.id in :courseIds
              and cs.linkedTopic is not null
              and cs.deletedAt is null
              and cs.textContent is not null
              and lower(cs.textContent) like lower(concat('%', :q, '%'))
            """)
    List<TopicExplanationSearchResultDto> searchLinkedExplanations(@Param("courseIds") List<Long> courseIds, @Param("q") String q);
}
