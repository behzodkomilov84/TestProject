package behzoddev.testproject.dto.testsession;

import java.time.LocalDateTime;

// "📊 Statistika" menyusiga qo'shilgan yangi sahifa — "Foydalanuvchilar
// kesimida test sessiyasi statistikasi" (foydalanuvchi so'rovi,
// 2026-09-13). Har bir FOYDALANUVCHI uchun BITTA qator — barcha
// TUGATILGAN (finishedAt != null) test sessiyalari bo'yicha yig'ma
// ko'rsatkichlar (UserRepository#findAllUserTestSessionStats).
// <p>
// Kompakt konstruktor — LEFT JOIN natijasida hali test yechmagan
// foydalanuvchilar uchun agregat ustunlar (count/sum/avg/max) SQL
// darajasida NULL bo'lib qaytadi (Hibernate buni Java "null" ga
// aylantiradi) — frontendda "N/A" bilan emas, tekshiruvsiz "0" bilan
// ishlash uchun shu yerda darhol normallashtiriladi.
public record UserTestSessionStatsDto(
        Long userId,
        String username,
        String firstName,
        String lastName,
        String groupName,
        Long sessionCount,
        Long totalQuestions,
        Long totalCorrect,
        Double avgPercent,
        Integer bestPercent,
        Long totalDurationSec,
        LocalDateTime lastSessionAt
) {
    public UserTestSessionStatsDto {
        sessionCount = sessionCount != null ? sessionCount : 0L;
        totalQuestions = totalQuestions != null ? totalQuestions : 0L;
        totalCorrect = totalCorrect != null ? totalCorrect : 0L;
        avgPercent = avgPercent != null ? avgPercent : 0.0;
        bestPercent = bestPercent != null ? bestPercent : 0;
        totalDurationSec = totalDurationSec != null ? totalDurationSec : 0L;
    }
}
