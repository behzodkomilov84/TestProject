package behzoddev.testproject.dto.backup;

import java.util.List;

// "♻️ Tanlanganlarni tiklash" natijasi — nechta yozuv qaysi jadvaldan
// tiklangani va qaysi kurslar (allaqachon jonli bazada bor bo'lgani yoki
// vaqt oralig'idan tashqarida qolgani uchun) o'tkazib yuborilgani.
public record BackupRestoreResultDto(
        int restoredCourses,
        int restoredChapters,
        int restoredSections,
        int restoredSubscriptions,
        int restoredProgress,
        List<Long> skippedCourseIds) {
}
