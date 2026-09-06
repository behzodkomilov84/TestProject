package behzoddev.testproject.dto.feedback;

// Status o'zgartirish so'rovi (FeedbackController) — FAQAT OWNER/ADMIN
// uchun (FeedbackService#updateStatus tekshiradi). "status" — FeedbackStatus
// enum nomi (masalan "IN_PROGRESS"), noto'g'ri qiymat bo'lsa 400 qaytadi.
public record FeedbackStatusUpdateDto(String status) {
}
