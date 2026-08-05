package behzoddev.testproject.dto.course;

// Bo'lim to'liq kontenti — faqat ochilgan (unlock qilingan) bo'lim uchun qaytariladi.
@lombok.Builder
public record CourseSectionContentDto(
        Long id,
        Long courseId,
        String courseTitle,
        String title,
        int orderIndex,
        String type, // TEXT | VIDEO
        String textContent,
        String videoSourceType, // UPLOAD | YOUTUBE | EXTERNAL
        String videoUrl,
        Integer videoDurationSeconds,
        boolean completed,
        Long nextSectionId, // null bo'lsa — bu oxirgi bo'lim
        boolean nextUnlocked
) {
}
