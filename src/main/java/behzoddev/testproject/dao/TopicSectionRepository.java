package behzoddev.testproject.dao;

import behzoddev.testproject.dto.section.TopicSectionIdAndNameDto;
import behzoddev.testproject.dto.section.TopicSectionTrashDto;
import behzoddev.testproject.entity.TopicSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// DIQQAT: TopicSection'da "O'chirilganlar savati" (deletedAt) bor —
// listing metodlari "deletedAt is null" filtri bilan yozilgan.
// existsByScience_IdAndNameIgnoreCase/findByScience_IdAndNameIgnoreCase
// ATAYLAB filtrlanmagan — Science'dagi bilan bir xil sabab (UNIQUE(name)
// cheklovi, "allaqachon mavjud" xabari, tiklashni taklif qilish).
public interface TopicSectionRepository extends JpaRepository<TopicSection, Long> {

    // topicCount — shu Bo'limdagi mavzular soni (topic-sections.html'da
    // "(N ta mavzu)" ko'rsatish uchun). Korrelyatsiyalangan subso'rov —
    // count() doim aniq bitta qatorli, fan-out xavfi yo'q.
    @Query("select new behzoddev.testproject.dto.section.TopicSectionIdAndNameDto(s.id, s.name, s.orderIndex, " +
            "(select count(t) from Topic t where t.section = s and t.deletedAt is null)) " +
            "from TopicSection s where s.science.id = :scienceId and s.deletedAt is null order by s.orderIndex")
    List<TopicSectionIdAndNameDto> findByScienceIdOrderByOrderIndex(@Param("scienceId") Long scienceId);

    @Query("select s from TopicSection s where s.science.id = :scienceId and s.deletedAt is null order by s.orderIndex asc")
    List<TopicSection> findByScience_IdOrderByOrderIndexAsc(@Param("scienceId") Long scienceId);

    boolean existsByScience_IdAndNameIgnoreCase(Long scienceId, String name);

    // Kurs mavzusi (CourseSection) Bo'limi (CourseChapter) bilan Fan/Mavzu
    // bog'langanda — TEST BOSHQARUVI tomonida ham shu nomli Bo'lim (agar
    // mavjud bo'lmasa) avtomatik topiladi/yaratiladi (CourseService.
    // resolveTopicSection). Nom katta-kichik harfga sezgir emas — bir xil
    // nomli bo'lim ikki marta yaratilib qolmasligi uchun.
    Optional<TopicSection> findByScience_IdAndNameIgnoreCase(Long scienceId, String name);

    @Query("select max(s.orderIndex) from TopicSection s where s.science.id = :scienceId")
    Integer findMaxOrderIndexByScienceId(@Param("scienceId") Long scienceId);

    // "O'chirilganlar savati" ro'yxati (Fan ichida) — TopicSectionService.getDeletedSections.
    @Query("select new behzoddev.testproject.dto.section.TopicSectionTrashDto(s.id, s.name, s.deletedAt) " +
            "from TopicSection s where s.science.id = :scienceId and s.deletedAt is not null order by s.deletedAt desc")
    List<TopicSectionTrashDto> findDeletedByScienceId(@Param("scienceId") Long scienceId);
}
