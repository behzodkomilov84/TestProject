package behzoddev.testproject.dto.course;

// Kurs yaratish/tahrirlash uchun (OWNER, /api/courses).
public record CourseSaveDto(String title, String description, String coverImageUrl, Boolean published) {
}
