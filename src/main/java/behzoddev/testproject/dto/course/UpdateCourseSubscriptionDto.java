package behzoddev.testproject.dto.course;

import java.math.BigDecimal;

// "/courses/subscriptions" sahifasidagi "✏️ Tahrirlash" — mavjud
// obunaning summasi va muddatini o'zgartirish uchun (foydalanuvchi
// so'rovi, 2026-09-09).
public record UpdateCourseSubscriptionDto(BigDecimal amount, Integer durationMonths) {
}
