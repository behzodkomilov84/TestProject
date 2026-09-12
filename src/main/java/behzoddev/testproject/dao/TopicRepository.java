package behzoddev.testproject.dao;

import behzoddev.testproject.dto.section.SectionTopicCountDto;
import behzoddev.testproject.dto.topic.TestHierarchyRowDto;
import behzoddev.testproject.dto.topic.TopicIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicTrashDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    // ORDER BY t.orderIndex — aniq tartib maydoni (ilgari "ORDER BY t.id"
    // workaround ishlatilgan edi, haqiqiy production bug: Kimyo fanining
    // 1-45 tartibli mavzulari aralashib chiqib qolgan edi).
    // LEFT JOIN t.section — MUHIM: oddiy "t.section.id" implicit INNER
    // JOIN hosil qilib, section=NULL bo'lgan mavzularni natijadan
    // butunlay chiqarib tashlashi mumkin edi (aynan shu xato turi
    // yuqoridagi t.id workaround'iga sabab bo'lgan edi) — shu sabab
    // ataylab aniq LEFT JOIN ishlatilgan.
    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "/topics
    // sahifa juda sekin yuklanyapti" — bu yerda ILGARI har bir mavzu
    // uchun IKKITA korrelyatsiyalangan subso'rov (savollar soni + savatdagi
    // savollar soni) ishlatuvchi so'rov bor edi — ko'p mavzuli Fanlarda
    // (masalan Bakteriologiya, 500+ mavzu) bu minglab subso'rovga yetib,
    // sahifani sekinlashtirar edi. Endi bu yengil versiya HECH QANDAY
    // subso'rovsiz — savollar soni TopicService.getTopicsByScienceId'da
    // ALOHIDA, BULK (GROUP BY) so'rov bilan (QuestionRepository.
    // countByTopicIdsGrouped/countDeletedByTopicIdsGrouped) to'ldiriladi,
    // xuddi linkedCourseTitle allaqachon qilinayotgani kabi.
    @Query("select new behzoddev.testproject.dto.topic.TopicIdAndNameDto(t.id, t.name, s.id) " +
            "from Topic t LEFT JOIN t.section s where t.science.id = :id and t.deletedAt is null order by t.orderIndex")
    List<TopicIdAndNameDto> findTopicBasicsByScienceId(@Param("id") Long id);

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "TEST
    // BOSHQARUVI dagi barcha N+1 so'rov muammolarini ko'rib chiq") —
    // TopicSectionRepository.findByScienceIdOrderByOrderIndex() HAR BIR
    // Bo'lim uchun korrelyatsiyalangan subso'rov ishlatardi (topicCount).
    // Bu — countByTopicIdsGrouped (QuestionRepository) bilan bir xil
    // g'oya, faqat Bo'lim->Mavzu darajasida: BULK, GROUP BY.
    @Query("select new behzoddev.testproject.dto.section.SectionTopicCountDto(t.section.id, count(t)) " +
            "from Topic t where t.section.id in :sectionIds and t.deletedAt is null group by t.section.id")
    List<SectionTopicCountDto> countBySectionIdsGrouped(@Param("sectionIds") List<Long> sectionIds);

    @Query("select new behzoddev.testproject.dto.topic.TopicIdAndNameDto(t.id, t.name, s.id, " +
            "(select count(q) from Question q where q.topic = t and q.deletedAt is null), " +
            "(select count(q) from Question q where q.topic = t and q.deletedAt is not null)) " +
            "from Topic t LEFT JOIN t.section s where t.science.id = :scienceId and t.id = :topicId and t.deletedAt is null")
    TopicIdAndNameDto findTopicByIds(@Param("scienceId") Long scienceId, @Param("topicId") Long topicId);

    // Kurs bo'limini TEST BOSHQARUVI'dagi mavzuga bog'lashda — mavjud
    // mavzuni topish uchun (CourseService.resolveLinkedTopic).
    Optional<Topic> findByScience_IdAndName(Long scienceId, String name);

    @Query("select t.science.id from Topic t where t.id = :topicId")
    Long getScienceIdByTopicId(@Param("topicId") Long topicId);

    @Query("UPDATE Topic t set t.name=:newName where t.id=:id")
    @Modifying
    void updateTopicName(@Param("id") Long id, @Param("newName") String newName);

    @Query("select t from Topic t where t.id = :id")
    Topic getTopicById(@Param("id") Long id);

    // Bo'limi bor mavzular avval (bo'lim tartibi, keyin mavzu tartibi
    // bo'yicha), bo'limsizlar oxirida (hozirgidek, testConfigPage.js'da
    // sarlavhasiz/tekis ro'yxat sifatida ko'rsatiladi).
    @Query("select new behzoddev.testproject.dto.topic.TopicWithQuestionCountDto(" +
            "t.id, t.name, count(q.id), s.id, s.name, s.orderIndex) " +
            "FROM Topic t LEFT JOIN Question q ON q.topic.id = t.id AND q.deletedAt IS NULL LEFT JOIN t.section s " +
            "WHERE t.science.id = :scienceId AND t.deletedAt IS NULL " +
            "GROUP BY t.id, t.name, t.orderIndex, s.id, s.name, s.orderIndex " +
            "ORDER BY CASE WHEN s.id IS NULL THEN 1 ELSE 0 END, s.orderIndex, t.orderIndex")
    List<TopicWithQuestionCountDto> getTopicsWithQuestionCount(@Param("scienceId") Long scienceId);

    List<Topic> findBySection_IdAndDeletedAtIsNullOrderByOrderIndexAsc(Long sectionId);

    List<Topic> findByScience_IdAndSectionIsNullAndDeletedAtIsNullOrderByOrderIndexAsc(Long scienceId);

    // Reorder (⬆⬇, A-Z/Z-A) uchun — TopicService.reorderTopics.
    List<Topic> findByScience_IdAndDeletedAtIsNullOrderByOrderIndexAsc(Long scienceId);

    @Query("select max(t.orderIndex) from Topic t where t.science.id = :scienceId")
    Integer findMaxOrderIndexByScienceId(@Param("scienceId") Long scienceId);

    // "O'chirilganlar savati" (Fan ichida) — TopicService.getDeletedTopics.
    // "left join t.section s" — foydalanuvchi so'rovi, 2026-09-12: "TEST
    // BOSHQARUVI dagi 'O'chirilgan darslar' panelini ham kursga
    // bog'lanmagan darslar kabi qil" (guruhlash uchun). ATAYLAB "left"
    // join — oddiy "t.section.id" (implicit join) "section"i NULL
    // (Bo'limsiz) mavzularni natijadan BUTUNLAY olib tashlagan bo'lardi.
    @Query("select new behzoddev.testproject.dto.topic.TopicTrashDto(t.id, t.name, t.deletedAt, " +
            "(select count(q) from Question q where q.topic = t), s.id, s.name) " +
            "from Topic t left join t.section s " +
            "where t.science.id = :scienceId and t.deletedAt is not null order by t.deletedAt desc")
    List<TopicTrashDto> findDeletedByScienceId(@Param("scienceId") Long scienceId);

    // testConfigPage.html uchun TO'LIQ to'rt darajali ierarxiya (Yo'nalish
    // -> Bo'lim -> Mavzu -> Dars) BITTA so'rovda, tekis qatorlar sifatida
    // (foydalanuvchi so'rovi, 2026-09-07). getTopicsWithQuestionCount
    // bilan bir xil andoza (LEFT JOIN Question ON ... AND deletedAt IS
    // NULL + GROUP BY), faqat scienceId bo'yicha filtrlanmaydi va
    // Fan/Yo'nalish qatlamlari ham qo'shiladi. O'chirilgan Fan/Yo'nalish
    // — chetlab o'tiladi (o'chirilgan Yo'nalishga hech qanday faol Fan
    // bog'lanmagan bo'lishi kerak, lekin xavfsizlik uchun aniq tekshiriladi).
    @Query("select new behzoddev.testproject.dto.topic.TestHierarchyRowDto(" +
            "f.id, f.name, sc.id, sc.name, sec.id, sec.name, t.id, t.name, count(q.id)) " +
            "FROM Topic t " +
            "JOIN t.science sc " +
            "LEFT JOIN sc.field f ON f.deletedAt IS NULL " +
            "LEFT JOIN t.section sec " +
            "LEFT JOIN Question q ON q.topic.id = t.id AND q.deletedAt IS NULL " +
            "WHERE t.deletedAt IS NULL AND sc.deletedAt IS NULL " +
            "GROUP BY f.id, f.name, f.orderIndex, sc.id, sc.name, sc.orderIndex, " +
            "sec.id, sec.name, sec.orderIndex, t.id, t.name, t.orderIndex " +
            "ORDER BY CASE WHEN f.id IS NULL THEN 1 ELSE 0 END, f.orderIndex, " +
            "sc.orderIndex, CASE WHEN sec.id IS NULL THEN 1 ELSE 0 END, sec.orderIndex, t.orderIndex")
    List<TestHierarchyRowDto> findFullHierarchy();
}
