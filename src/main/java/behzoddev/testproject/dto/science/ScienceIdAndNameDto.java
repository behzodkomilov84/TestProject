package behzoddev.testproject.dto.science;

import jakarta.validation.constraints.NotBlank;

public record ScienceIdAndNameDto(
        Long id,
        @NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name,
        // Shu fanda (UI'da "Bo'lim") nechta Bo'lim (TopicSection, UI'da
        // "Mavzu") borligi — science.html'da har bir qatorda ko'rsatish
        // uchun (masalan "(3 ta mavzu)"). Ixtiyoriy — boshqa (bot,
        // courseDetail.js) ishlatuvchilar buni e'tiborsiz qoldiradi.
        long sectionCount,
        // Qaysi Yo'nalishga tegishli — science.js shu bo'yicha guruhlaydi
        // (courses.js#renderFieldBox bilan bir xil andoza). Ixtiyoriy —
        // boshqa ishlatuvchilar buni e'tiborsiz qoldiradi.
        Long fieldId,
        String fieldName) {

    // Orqaga moslik — ko'p joyda (bot, testlar) hali 2 argumentli
    // konstruktor ishlatiladi, qolganlari ular uchun ahamiyatsiz (0/null).
    public ScienceIdAndNameDto(Long id, String name) {
        this(id, name, 0, null, null);
    }

    // Orqaga moslik — sectionCount kerak, lekin fieldId/fieldName kerak
    // bo'lmagan eski chaqiruv joylari uchun.
    public ScienceIdAndNameDto(Long id, String name, long sectionCount) {
        this(id, name, sectionCount, null, null);
    }
}
