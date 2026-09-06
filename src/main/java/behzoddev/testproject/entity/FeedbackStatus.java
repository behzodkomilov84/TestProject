package behzoddev.testproject.entity;

// "Fikr va takliflar" — OWNER/ADMIN har bir fikr-taklifga belgilaydigan
// holat (foydalanuvchi so'rovi, 2026-09-06). Oddiy foydalanuvchilar buni
// FAQAT o'qiydi (frontend'da rangli chip sifatida), o'zgartira olmaydi —
// FeedbackService#updateStatus shu cheklovni tekshiradi.
public enum FeedbackStatus {
    OPEN,
    IN_PROGRESS,
    DONE,
    REJECTED
}
