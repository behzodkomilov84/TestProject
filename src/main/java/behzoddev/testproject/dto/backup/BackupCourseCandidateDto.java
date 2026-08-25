package behzoddev.testproject.dto.backup;

import java.time.LocalDateTime;

// Tanlangan backup faylida, tanlangan vaqt oralig'ida topilgan bitta kurs
// (BackupRestoreService.previewCourses) — "🔍 Ko'rish" bosilganda, jonli
// bazaga hech narsa yozilmasdan OLDIN admin ko'rib chiqishi uchun.
// alreadyExistsLive=true bo'lsa — bu kurs jonli bazada ALLAQACHON bor
// (faol yoki O'chirilganlar savatida), shu sabab tiklashda AVTOMATIK
// o'tkazib yuboriladi (qayta yozilmaydi).
public record BackupCourseCandidateDto(Long id, String title, LocalDateTime createdAt, boolean alreadyExistsLive) {
}
