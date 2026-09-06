package behzoddev.testproject.dto.feedback;

import java.util.List;

// Ro'yxat + joriy foydalanuvchi OWNER/ADMIN'mi (moderatsiya — BOSHQA
// foydalanuvchilarning yozuvlarini o'chirish va statusini o'zgartirish
// huquqi) — frontend shu bo'yicha hal qiladi (feedback.js).
public record FeedbackThreadsResponseDto(List<FeedbackThreadDto> threads, boolean canManage) {
}
