package behzoddev.testproject.dto.teacher;

// "Barcha savol to'plamlari" (FAQAT ROLE_OWNER) — har bir o'qituvchi/admin
// o'zi yaratgan to'plamlarni FAQAT o'zida ko'radi (TeacherService.getSets —
// findByTeacher), lekin OWNER hammasini bir joyda ko'rishi kerak (kim
// nechta to'plam yaratgani, ularda nechta savol borligi).
public record QuestionSetAdminRowDto(Long id, String name, String teacherUsername, long questionCount) {
}
