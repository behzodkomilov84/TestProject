package behzoddev.testproject.dto.course;

import java.time.LocalDateTime;

// Faqat ROLE_OWNER uchun — ADMIN "🗑️ Butunlay o'chirish" orqali
// arxivlagan (lekin bazadan HAQIQIY o'chirilmagan) kurs
// (CourseService.getAdminArchivedCourses). "archivedByAdminName" — kim
// arxivlagani, "archivedAt" — qachon; OWNER shu ma'lumot bilan xohlasa
// "📤 O'zim nomimdan qayta nashr qilish" (reclaimArchivedCourse) orqali
// kursni qaytadan tiklashi mumkin.
public record CourseArchivedByAdminDto(
        Long id,
        String title,
        String archivedByAdminName,
        LocalDateTime archivedAt
) {
}
