package behzoddev.testproject.dto.teacher;

import java.time.LocalDateTime;

// "Barcha savol to'plamlari" (FAQAT ROLE_OWNER) — har bir o'qituvchi/admin
// o'zi yaratgan to'plamlarni FAQAT o'zida ko'radi (TeacherService.getSets —
// findByTeacher), lekin OWNER hammasini bir joyda, kim/qachon yaratgani va
// nechta savol borligi bilan ko'rishi kerak. Ichidagi savollarning O'ZI —
// alohida, tanlanganda so'raladi (TeacherController.getQuestionSetDetail —
// getSetDetail'da OWNER cheklovi yo'q, faqat "o'zi emas" tekshiruvi bor,
// shu sabab OWNER ham TO'LIQ tarkibni shu orqali ko'ra oladi).
public record QuestionSetAdminRowDto(Long id, String name, String teacherUsername, long questionCount, LocalDateTime createdAt) {
}
