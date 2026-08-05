package behzoddev.testproject.dto.subscription;

import lombok.Builder;

import java.math.BigDecimal;

// Bitta oy uchun tushum (faqat CONFIRMED to'lovlar bo'yicha).
// "month" — "yyyy-MM" formatida (masalan "2026-08"), frontend o'zi
// lokalizatsiya qilib chiqaradi.
@Builder
public record MonthlyRevenueDto(String month, BigDecimal amount, long count) {
}
