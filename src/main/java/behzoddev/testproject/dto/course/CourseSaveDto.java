package behzoddev.testproject.dto.course;

import java.math.BigDecimal;

// Kurs (Bo'lim) yaratish/tahrirlash uchun (OWNER/ADMIN, /api/courses).
// free=true bo'lsa — kurs obunasiz ham (site'da HAM, Telegram bot'da HAM)
// hammaga to'liq ochiq bo'ladi. price — pullik kurs uchun ko'rsatiladigan
// narx (free=true bo'lsa mazmunsiz, e'tiborga olinmaydi). fieldId —
// qaysi Yo'nalishga tegishli — YANGI kurs yaratishda MAJBURIY
// (CourseService.createCourse null bo'lsa xato qaytaradi), tahrirlashda
// ham har doim yuboriladi (aks holda mavjud bog'lanish yo'qolib qolmasin).
// sequentialUnlock — NULL bo'lsa: yaratishda TRUE (standart, ketma-ket)
// qabul qilinadi, tahrirlashda esa mavjud qiymat o'zgarishsiz qoladi
// (foydalanuvchi so'rovi, 2026-09-07: "барча дарслар очиқ бўлиши ёки...
// фақат 1-дарслари очиқ бўлишини танлаш имкони бўлсин").
public record CourseSaveDto(
        String title, String description, String coverImageUrl,
        Boolean published, Boolean free, BigDecimal price, Long fieldId,
        Boolean sequentialUnlock
) {
}
