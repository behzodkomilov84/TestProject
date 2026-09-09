package behzoddev.testproject.dto.subscription;

import java.math.BigDecimal;

// "/admin-subscriptions" sahifasidagi "✏️ Tahrirlash" — mavjud ADMIN-rol
// obunasining summasi va muddatini o'zgartirish uchun (foydalanuvchi
// so'rovi, 2026-09-09).
public record UpdateSubscriptionDto(BigDecimal amount, Integer durationMonths) {
}
