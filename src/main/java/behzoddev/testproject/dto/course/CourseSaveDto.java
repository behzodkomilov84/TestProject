package behzoddev.testproject.dto.course;

import java.math.BigDecimal;

// Kurs (Bo'lim) yaratish/tahrirlash uchun (OWNER/ADMIN, /api/courses).
// free=true bo'lsa — kurs obunasiz ham (site'da HAM, Telegram bot'da HAM)
// hammaga to'liq ochiq bo'ladi. price — pullik kurs uchun ko'rsatiladigan
// narx (free=true bo'lsa mazmunsiz, e'tiborga olinmaydi). fieldId —
// qaysi Yo'nalishga tegishli — YANGI kurs yaratishda MAJBURIY
// (CourseService.createCourse null bo'lsa xato qaytaradi), tahrirlashda
// ham har doim yuboriladi (aks holda mavjud bog'lanish yo'qolib qolmasin).
public record CourseSaveDto(
        String title, String description, String coverImageUrl,
        Boolean published, Boolean free, BigDecimal price, Long fieldId
) {
}
