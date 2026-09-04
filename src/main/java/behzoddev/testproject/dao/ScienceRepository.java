package behzoddev.testproject.dao;

import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.science.ScienceTrashDto;
import behzoddev.testproject.entity.Science;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ScienceRepository extends JpaRepository<Science, Long> {

    // DIQQAT: Science'da "O'chirilganlar savati" (deletedAt) bor —
    // quyidagi "o'qish" metodlari ATAYLAB "deletedAt is null" filtri
    // bilan yozilgan. findByName/existsByName esa ATAYLAB
    // FILTRLANMAGAN — bu ataylab shunday: soft-delete qilingan fan
    // bilan bir xil nomli YANGI fan yaratishga urinilsa, "bunday fan
    // allaqachon mavjud" xabari chiqishi kerak (bazadagi UNIQUE(name)
    // cheklovi baribir bunga yo'l qo'ymaydi — bu shunchaki oldindan,
    // aniqroq xabar bilan bloklaydi, tiklashni taklif qiladi).

    @EntityGraph(value = "scienceWithTopics")
    @Query("select s from Science s where s.deletedAt is null order by s.orderIndex")
    Set<Science> findAllWithTopics();

    @EntityGraph(value = "scienceWithTopics")
    @Query("select s from Science s where s.id = :id and s.deletedAt is null")
    Optional<Science> findByIdWithTopics(Long id);


    // sectionCount — shu fandagi Bo'limlar (TopicSection, UI'da "Mavzu")
    // soni, science.html qatorida ko'rsatish uchun ("(N ta mavzu)").
    // Korrelyatsiyalangan subso'rov (har bir fan uchun bitta son
    // qaytaradi — count() doim aniq bitta qatorli, fan-out xavfi yo'q).
    // fieldId/fieldName — science.js#renderFieldBox Yo'nalish bo'yicha
    // guruhlashi uchun. MUHIM: "left join s.field f" ATAYLAB — agar
    // "s.field.id" to'g'ridan-to'g'ri SELECT'da yozilsa, JPQL buni
    // IMPLICIT INNER JOIN deb talqin qiladi, natijada Yo'nalishga hali
    // tayinlanmagan (field=NULL) fanlar BUTUNLAY natijadan tushib qolar
    // edi (haqiqiy topilgan bug — "Ona tili" ro'yxatdan g'oyib bo'lgan edi).
    @Query("select new behzoddev.testproject.dto.science.ScienceIdAndNameDto(s.id, s.name, " +
            "(select count(ts) from TopicSection ts where ts.science = s and ts.deletedAt is null), " +
            "f.id, f.name) " +
            "from Science s left join s.field f " +
            "where s.deletedAt is null order by s.orderIndex")
    Set<ScienceIdAndNameDto> findAllScienceNames();

    // Reorder (⬆⬇, A-Z/Z-A) uchun — ScienceService.reorderSciences.
    @Query("select s from Science s where s.deletedAt is null order by s.orderIndex")
    List<Science> findAllByDeletedAtIsNullOrderByOrderIndex();

    @Query("select max(s.orderIndex) from Science s")
    Integer findMaxOrderIndex();

    @Query("select new behzoddev.testproject.dto.science.ScienceIdAndNameDto(s.id, s.name) " +
            "from Science s where s.id = :id and s.deletedAt is null")
    Optional<ScienceIdAndNameDto> findScienceNameById(@Param("id") Long id);

    @Query("select s from Science s where s.name = :name")
    Optional<Science> findByName(@Param("name") String name);

    @Query("UPDATE Science s set s.name=:name where s.id=:id")
    @Modifying
    void updateScienceName(@Param("id") Long id, @Param("name") String name);

    @Transactional
    boolean existsByName(String name);

    // "O'chirilganlar savati" ro'yxati — ScienceService.getDeletedSciences.
    @Query("select new behzoddev.testproject.dto.science.ScienceTrashDto(s.id, s.name, s.deletedAt) " +
            "from Science s where s.deletedAt is not null order by s.deletedAt desc")
    List<ScienceTrashDto> findAllDeleted();
}
