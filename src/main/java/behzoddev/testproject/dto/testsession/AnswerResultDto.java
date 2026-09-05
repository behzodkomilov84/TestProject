package behzoddev.testproject.dto.testsession;

import java.util.List;

// "answerId" — eski (bitta javobli) mijozlar uchun, hamon to'liq
// qo'llab-quvvatlanadi. "answerIds" — ko'p to'g'ri javobli savollar
// uchun (foydalanuvchi so'rovi, 2026-09-05) — YANGI mijoz shu maydonni
// to'ldiradi (bitta javobli savolda ham, bitta elementli ro'yxat bilan).
// Ikkalasi ham berilsa — "answerIds" ustun keladi (TestSessionService
// #resolveSubmittedAnswerIds).
public record AnswerResultDto(Long questionId, Long answerId, List<Long> answerIds) {

    // Orqaga moslik — TelegramPracticeTestService kabi eski chaqiruv
    // joylari uchun (hali "answerIds" bilan yangilanmagan).
    public AnswerResultDto(Long questionId, Long answerId) {
        this(questionId, answerId, null);
    }
}
