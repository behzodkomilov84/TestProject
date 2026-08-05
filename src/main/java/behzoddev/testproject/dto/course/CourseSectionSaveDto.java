package behzoddev.testproject.dto.course;

// Bo'lim yaratish/tahrirlash uchun (OWNER, /api/courses/{courseId}/sections).
public record CourseSectionSaveDto(
        String title,
        String type, // TEXT | VIDEO
        String textContent,
        String videoSourceType, // UPLOAD | YOUTUBE | EXTERNAL
        String videoUrl,
        Integer videoDurationSeconds
) {
}
