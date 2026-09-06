package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseSectionProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseSectionProgressRepository extends JpaRepository<CourseSectionProgress, Long> {

    boolean existsByUser_IdAndSection_Id(Long userId, Long sectionId);

    List<CourseSectionProgress> findByUser_IdAndSection_Course_Id(Long userId, Long courseId);

    // Kursni o'chirishdan oldin — foreign key RESTRICT bo'lgani uchun,
    // avval shu kursning istalgan bo'limiga tegishli barcha progress
    // yozuvlarini o'chirish kerak (CourseService.deleteCourse).
    void deleteBySection_Course_Id(Long courseId);

    // Bitta bo'limni o'chirishdan oldin — xuddi shunday FK RESTRICT sababli
    // (CourseService.deleteSection). Aks holda "Cannot delete or update a
    // parent row" xatosi bilan muvaffaqiyatsiz tugardi (foydalanuvchi shu
    // bo'limni "tugatilgan" deb belgilagan bo'lsa).
    void deleteBySection_Id(Long sectionId);

    // Foydalanuvchini o'chirishdan OLDIN (UserServiceImpl#deleteUser) —
    // "user_id" FK RESTRICT (notifications/role_audit_logs'dagi bilan
    // bir xil muammo, 2026-09-06) — yuqoridagi ikkitasidan farqli, bu
    // yerda FOYDALANUVCHI bo'yicha (kurs/bo'lim emas).
    void deleteByUser_Id(Long userId);
}
