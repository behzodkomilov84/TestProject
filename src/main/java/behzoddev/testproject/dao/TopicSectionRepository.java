package behzoddev.testproject.dao;

import behzoddev.testproject.dto.science.ScienceSectionCountDto;
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

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "TEST
    // BOSHQARUVI dagi barcha N+1 so'rov muammolarini ko'rib chiq") — bu
    // yerda ILGARI har bir Bo'lim uchun korrelyatsiyalangan subso'rov
    // (topicCount) ishlatuvchi bitta katta so'rov bor edi. Endi bu yengil
    // versiya subso'rovsiz, mavzular soni TopicSectionService'da ALOHIDA,
    // BULK (GROUP BY) so'rov bilan (TopicRepository.countBySectionIdsGrouped)
    // to'ldiriladi (QuestionRepository.countByTopicIdsGrouped bilan bir
    // xil g'oya).
    @Query("select new behzoddev.testproject.dto.section.TopicSectionIdAndNameDto(s.id, s.name, s.orderIndex) " +
            "from TopicSection s where s.science.id = :scienceId and s.deletedAt is null order by s.orderIndex")
    List<TopicSectionIdAndNameDto> findSectionBasicsByScienceId(@Param("scienceId") Long scienceId);

    // /science/fields sahifasidagi (Fanlar ro'yxati, ScienceRepository.
    // findAllScienceNames() bilan bir xil, HAQIQIY TOPILGAN BUG,
    // 2026-09-12) Bo'lim soni endi shu BULK (GROUP BY) so'rov bilan
    // — har bir Fan uchun korrelyatsiyalangan subso'rov o'rniga.
    @Query("select new behzoddev.testproject.dto.science.ScienceSectionCountDto(s.science.id, count(s)) " +
            "from TopicSection s where s.science.id in :scienceIds and s.deletedAt is null group by s.science.id")
    List<ScienceSectionCountDto> countByScienceIdsGrouped(@Param("scienceIds") List<Long> scienceIds);

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
