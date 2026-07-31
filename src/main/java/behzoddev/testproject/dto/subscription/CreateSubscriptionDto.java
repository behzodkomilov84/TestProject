package behzoddev.testproject.dto.subscription;

import java.math.BigDecimal;

// OWNER tomonidan qo'lda (naqd/karta kabi saytdan tashqarida qabul qilingan
// to'lov uchun) darhol tasdiqlangan obuna yaratish uchun.
public record CreateSubscriptionDto(
        Long userId,
        BigDecimal amount,
        Integer durationMonths,
        String note
) {
}
