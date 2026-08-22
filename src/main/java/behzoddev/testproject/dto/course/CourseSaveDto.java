package behzoddev.testproject.dto.course;

// Kurs yaratish/tahrirlash uchun (OWNER, /api/courses).
// free=true bo'lsa — kurs obunasiz ham (site'da HAM, Telegram bot'da HAM)
// hammaga to'liq ochiq bo'ladi.
public record CourseSaveDto(String title, String description, String coverImageUrl, Boolean published, Boolean free) {
}
