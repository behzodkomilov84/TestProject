package behzoddev.testproject.dto.course;

// Bo'lim yaratish/tahrirlash uchun (OWNER, /api/courses/{courseId}/sections).
public record CourseSectionSaveDto(
        String title,
        String type, // TEXT | VIDEO
        String textContent,
        String videoSourceType, // UPLOAD | YOUTUBE | EXTERNAL
        String videoUrl,
        Integer videoDurationSeconds,
        // Ixtiyoriy — TEST BOSHQARUVI'dagi Fan/Mavzu bilan bog'lash uchun.
        // Ikkalasi ham berilsa: shu nomli Fan/Mavzu mavjud bo'lmasa avtomatik
        // yaratiladi (CourseService.resolveLinkedTopic). Bo'sh qoldirilsa —
        // bog'lanish olib tashlanadi (unlink).
        String scienceName,
        String topicName,
        // PLAIN (qo'lda yozilgan) | HTML (.docx'dan import qilingan —
        // frontend mammoth.js orqali fayldan HTML olib, textContent'ga shu
        // holicha yuboradi). null/bo'sh bo'lsa — PLAIN deb qabul qilinadi.
        String textContentFormat
) {
}
