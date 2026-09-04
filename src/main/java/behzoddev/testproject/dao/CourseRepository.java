package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // MUHIM: uchalasi ham endi soft-delete qilingan (deletedAt != null)
    // kurslarni ATAYLAB chetlab o'tadi — ular faqat "O'chirilganlar
    // savati"da (findAllDeletedOrderByDeletedAtDesc) ko'rinadi.
    @Query("SELECT c FROM Course c WHERE c.deletedAt IS NULL AND c.published = true ORDER BY c.createdAt DESC")
    List<Course> findByPublishedTrueOrderByCreatedAtDesc();

    @Query("SELECT c FROM Course c WHERE c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    List<Course> findAllByOrderByCreatedAtDesc();

    // ADMIN uchun katalog — chop etilgan barcha kurslar + o'zi yaratgan
    // qoralamalar ham (aks holda ADMIN o'zi hali chop etmagan kursini
    // katalogda topa olmay qolardi).
    @Query("SELECT c FROM Course c WHERE c.deletedAt IS NULL AND (c.published = true OR c.createdBy.id = :createdById) ORDER BY c.createdAt DESC")
    List<Course> findByPublishedTrueOrCreatedBy_IdOrderByCreatedAtDesc(@Param("createdById") Long createdById);

    // "O'chirilganlar savati" — OWNER uchun BARCHA soft-delete qilingan
    // kurslar (kim yaratganidan qat'i nazar). ADMIN allaqachon "Butunlay
    // o'chirish" qilib ARXIVLAGAN kurslar (archivedByAdmin != null) bu
    // yerda emas, ALOHIDA ro'yxatda (findArchivedByAdminOrderByArchivedAtDesc) —
    // ikkalasi aralashib ketmasin deb.
    @Query("SELECT c FROM Course c WHERE c.deletedAt IS NOT NULL AND c.archivedByAdmin IS NULL ORDER BY c.deletedAt DESC")
    List<Course> findAllDeletedOrderByDeletedAtDesc();

    // "O'chirilganlar savati" — ADMIN uchun faqat O'ZI yaratgan
    // soft-delete qilingan kurslar. Bu yerda ham archivedByAdmin != null
    // bo'lganlari chetlab o'tiladi — ADMIN "Butunlay o'chirish"ni bosgach,
    // bu kurs ENDI O'ZIDAN ham yo'qolishi kerak (foydalanuvchi ANIQ talabi).
    @Query("SELECT c FROM Course c WHERE c.deletedAt IS NOT NULL AND c.createdBy.id = :createdById AND c.archivedByAdmin IS NULL ORDER BY c.deletedAt DESC")
    List<Course> findDeletedByCreatedBy_IdOrderByDeletedAtDesc(@Param("createdById") Long createdById);

    // Faqat ROLE_OWNER uchun — ADMIN'lar "Butunlay o'chirish" orqali
    // arxivlagan (lekin bazadan HAQIQIY o'chirilmagan) kurslar ro'yxati
    // (CourseService.getAdminArchivedCourses) — kim/qachon arxivlagani
    // bilan, xohlasa o'z nomiga o'tkazib qayta tiklashi mumkin
    // (reclaimArchivedCourse).
    @Query("SELECT c FROM Course c WHERE c.archivedByAdmin IS NOT NULL ORDER BY c.archivedAt DESC")
    List<Course> findArchivedByAdminOrderByArchivedAtDesc();

    // Yo'nalish kartochkasida "N ta bo'lim" (coursesCatalog.js) —
    // CourseFieldService.toDto. Faqat FAOL (o'chirilmagan) kurslar sanaladi.
    long countByField_IdAndDeletedAtIsNull(Long fieldId);
}
