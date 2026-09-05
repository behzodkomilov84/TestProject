package behzoddev.testproject.dto.student;

import java.util.List;

// "selectedAnswerId" — eski (bitta javobli) mijoz uchun (hozirgi
// student-task.js hamon shu maydondan foydalanadi, o'zgartirilmagan).
// "selectedAnswerIds" — ko'p to'g'ri javobli savollar uchun (foydalanuvchi
// so'rovi, 2026-09-05) — kelajakdagi mijoz yangilanishi shu to'liq
// ro'yxatdan foydalanadi.
public record AttemptQuestionDto(
        Long questionId,
        Long selectedAnswerId,
        List<Long> selectedAnswerIds
) {
}
