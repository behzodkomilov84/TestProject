package behzoddev.testproject.dto.science;

import jakarta.validation.constraints.NotBlank;

public record ScienceIdAndNameDto(
        Long id,
        @NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name,
        // Shu fanda nechta Bo'lim (TopicSection) borligi — science.html'da
        // har bir fan qatorida ko'rsatish uchun (masalan "(3 ta bo'lim)").
        // Ixtiyoriy — boshqa (bot, courseDetail.js) ishlatuvchilar buni
        // e'tiborsiz qoldiradi.
        long sectionCount) {

    // Orqaga moslik — ko'p joyda (bot, testlar) hali 2 argumentli
    // konstruktor ishlatiladi, sectionCount ular uchun ahamiyatsiz (0).
    public ScienceIdAndNameDto(Long id, String name) {
        this(id, name, 0);
    }
}
