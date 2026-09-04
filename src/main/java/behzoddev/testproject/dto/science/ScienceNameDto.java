package behzoddev.testproject.dto.science;

import jakarta.validation.constraints.NotBlank;

public record ScienceNameDto(
        @NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name,
        // Yangi Bo'lim (Science) qaysi Yo'nalishga tegishli — IXTIYORIY
        // (foydalanuvchi so'rovi, 2026-09-05: kurslardan farqli, Bo'lim
        // uchun Yo'nalish majburiy emas — ko'p eski Bo'limlar hali
        // Yo'nalishga bog'lanmagan, ularni sindirmaslik uchun). null bo'lsa
        // — Yo'nalishsiz yaratiladi.
        Long fieldId
) {
    // Orqaga moslik — Yo'nalishsiz yaratish (eski 1-argumentli chaqiruvlar).
    public ScienceNameDto(String name) {
        this(name, null);
    }
}
