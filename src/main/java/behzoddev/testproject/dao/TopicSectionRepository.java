package behzoddev.testproject.dao;

import behzoddev.testproject.dto.section.TopicSectionIdAndNameDto;
import behzoddev.testproject.entity.TopicSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicSectionRepository extends JpaRepository<TopicSection, Long> {

    // topicCount — shu Bo'limdagi mavzular soni (topic-sections.html'da
    // "(N ta mavzu)" ko'rsatish uchun). Korrelyatsiyalangan subso'rov —
    // count() doim aniq bitta qatorli, fan-out xavfi yo'q.
    @Query("select new behzoddev.testproject.dto.section.TopicSectionIdAndNameDto(s.id, s.name, s.orderIndex, " +
            "(select count(t) from Topic t where t.section = s)) " +
            "from TopicSection s where s.science.id = :scienceId order by s.orderIndex")
    List<TopicSectionIdAndNameDto> findByScienceIdOrderByOrderIndex(@Param("scienceId") Long scienceId);

    List<TopicSection> findByScience_IdOrderByOrderIndexAsc(Long scienceId);

    boolean existsByScience_IdAndNameIgnoreCase(Long scienceId, String name);

    // Kurs mavzusi (CourseSection) Bo'limi (CourseChapter) bilan Fan/Mavzu
    // bog'langanda — TEST BOSHQARUVI tomonida ham shu nomli Bo'lim (agar
    // mavjud bo'lmasa) avtomatik topiladi/yaratiladi (CourseService.
    // resolveTopicSection). Nom katta-kichik harfga sezgir emas — bir xil
    // nomli bo'lim ikki marta yaratilib qolmasligi uchun.
    Optional<TopicSection> findByScience_IdAndNameIgnoreCase(Long scienceId, String name);

    @Query("select max(s.orderIndex) from TopicSection s where s.science.id = :scienceId")
    Integer findMaxOrderIndexByScienceId(@Param("scienceId") Long scienceId);
}
