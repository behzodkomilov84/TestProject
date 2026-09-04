package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CourseFieldRepository extends JpaRepository<CourseField, Long> {

    @Query("select f from CourseField f where f.deletedAt is null order by f.orderIndex asc")
    List<CourseField> findAllByOrderByOrderIndexAsc();

    @Query("select f from CourseField f where f.deletedAt is not null order by f.deletedAt desc")
    List<CourseField> findAllDeletedOrderByDeletedAtDesc();

    // Yangi Yo'nalish doim oxiriga qo'shiladi (CourseChapterRepository
    // bilan bir xil andoza).
    @Query("select f from CourseField f order by f.orderIndex desc limit 1")
    Optional<CourseField> findTopByOrderIndexDesc();

    // Yo'nalimni o'chirishdan oldin — bo'sh (hech qanday faol kursga
    // biriktirilmagan) ekanini tekshirish uchun (CourseFieldService.deleteField).
    @Query("select case when count(c) > 0 then true else false end from Course c " +
            "where c.field.id = :fieldId and c.deletedAt is null")
    boolean existsActiveCourseByField_Id(Long fieldId);
}
