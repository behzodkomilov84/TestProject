package behzoddev.testproject.dto.student;

import java.util.List;

// "selectedAnswerId" — eski (bitta javobli) mijozlar (Telegram bot ham
// hozircha shu orqali) uchun, hamon to'liq qo'llab-quvvatlanadi.
// "selectedAnswerIds" — ko'p to'g'ri javobli savollar uchun (foydalanuvchi
// so'rovi, 2026-09-05) — YANGI mijoz shu maydonni to'ldiradi.
public record AnswerSyncDto(
        Long questionId,
        Long selectedAnswerId,
        List<Long> selectedAnswerIds
) {

    // Orqaga moslik — TelegramBot kabi eski chaqiruv joylari uchun.
    public AnswerSyncDto(Long questionId, Long selectedAnswerId) {
        this(questionId, selectedAnswerId, null);
    }
}
