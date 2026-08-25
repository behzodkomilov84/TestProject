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
    // kurslar (kim yaratganidan qat'i nazar).
    @Query("SELECT c FROM Course c WHERE c.deletedAt IS NOT NULL ORDER BY c.deletedAt DESC")
    List<Course> findAllDeletedOrderByDeletedAtDesc();

    // "O'chirilganlar savati" — ADMIN uchun faqat O'ZI yaratgan
    // soft-delete qilingan kurslar.
    @Query("SELECT c FROM Course c WHERE c.deletedAt IS NOT NULL AND c.createdBy.id = :createdById ORDER BY c.deletedAt DESC")
    List<Course> findDeletedByCreatedBy_IdOrderByDeletedAtDesc(@Param("createdById") Long createdById);
}
