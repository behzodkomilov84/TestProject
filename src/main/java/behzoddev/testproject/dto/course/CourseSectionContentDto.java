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
        // Shu bo'lim aynan bitta mavzuga (Topic) bog'langan bo'lsa — "🎯 Mavzuga
        // oid testlarni yechish" tugmasini ko'rsatish uchun (/testConfigPage'ga
        // shu fan/mavzu avtomatik tanlangan holda o'tkazadi).
        Long linkedTopicId,
        Long linkedScienceId,
        // Tahrirlash formasini oldindan to'ldirish uchun — foydalanuvchi
        // ID'larni emas, fan/mavzu NOMINI ko'radi va o'zgartiradi.
        String linkedTopicName,
        String linkedScienceName,
        boolean completed,
        Long nextSectionId, // null bo'lsa — bu oxirgi bo'lim
        boolean nextUnlocked
) {
}
