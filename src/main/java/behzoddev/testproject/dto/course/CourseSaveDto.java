package behzoddev.testproject.dto.course;

import java.math.BigDecimal;

// Kurs yaratish/tahrirlash uchun (OWNER, /api/courses).
// free=true bo'lsa — kurs obunasiz ham (site'da HAM, Telegram bot'da HAM)
// hammaga to'liq ochiq bo'ladi. price — pullik kurs uchun ko'rsatiladigan
// narx (free=true bo'lsa mazmunsiz, e'tiborga olinmaydi).
public record CourseSaveDto(
        String title, String description, String coverImageUrl,
        Boolean published, Boolean free, BigDecimal price
) {
}
