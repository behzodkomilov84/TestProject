package behzoddev.testproject.dto.subscription;

import java.math.BigDecimal;

// "/admin-subscriptions" sahifasidagi "✏️ Tahrirlash" — mavjud ADMIN-rol
// obunasining summasi, muddati va manbasini o'zgartirish uchun
// (foydalanuvchi so'rovi, 2026-09-09: "Админ обуналарини таҳрирлашда
// манбасини ҳам таҳрирлаш мумкин бўлсин"). "source" ixtiyoriy — null/bo'sh
// bo'lsa, mavjud manba o'zgarishsiz qoladi (MANUAL/ONLINE/TELEGRAM).
public record UpdateSubscriptionDto(BigDecimal amount, Integer durationMonths, String source) {
}
