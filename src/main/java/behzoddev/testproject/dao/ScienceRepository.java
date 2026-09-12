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


    // fieldId/fieldName — science.js#renderFieldBox Yo'nalish bo'yicha
    // guruhlashi uchun. MUHIM: "left join s.field f" ATAYLAB — agar
    // "s.field.id" to'g'ridan-to'g'ri SELECT'da yozilsa, JPQL buni
    // IMPLICIT INNER JOIN deb talqin qiladi, natijada Yo'nalishga hali
    // tayinlanmagan (field=NULL) fanlar BUTUNLAY natijadan tushib qolar
    // edi (haqiqiy topilgan bug — "Ona tili" ro'yxatdan g'oyib bo'lgan edi).
    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "TEST
    // BOSHQARUVI dagi barcha N+1 so'rov muammolarini ko'rib chiq") — bu
    // yerda ILGARI har bir Fan uchun korrelyatsiyalangan subso'rov
    // (Bo'lim soni) bor edi. Endi bu yengil versiya subso'rovsiz — Bo'lim
    // soni ScienceService.getAllScienceIdAndNameDto'da ALOHIDA, BULK
    // (GROUP BY) so'rov bilan (TopicSectionRepository.countByScienceIdsGrouped)
    // to'ldiriladi. "0L" — sectionCount uchun vaqtinchalik joy tutuvchi
    // (keyin merge qilinadi).
    @Query("select new behzoddev.testproject.dto.science.ScienceIdAndNameDto(s.id, s.name, 0L, f.id, f.name) " +
            "from Science s left join s.field f " +
            "where s.deletedAt is null order by s.orderIndex")
    List<ScienceIdAndNameDto> findAllScienceBasics();

    // Reorder (⬆⬇, A-Z/Z-A) uchun — ScienceService.reorderSciences.
    @Query("select s from Science s where s.deletedAt is null order by s.orderIndex")
    List<Science> findAllByDeletedAtIsNullOrderByOrderIndex();

    @Query("select max(s.orderIndex) from Science s")
    Integer findMaxOrderIndex();

    // CourseFieldService.toDto — Yo'nalish kartochkasida "(N ta bo'lim)"
    // ko'rsatish uchun (CourseRepository.countByField_IdAndDeletedAtIsNull
    // bilan bir xil andoza).
    long countByField_IdAndDeletedAtIsNull(Long fieldId);

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
