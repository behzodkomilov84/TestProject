package behzoddev.testproject.dto.course;

// "Mavzu" kartochkasidagi "📥 Darslar + testlarni import qilish"
// (foydalanuvchi so'rovi, 2026-09-10) — HAR BIR .docx fayl uchun bitta
// element. "html" — brauzerda mammoth.js orqali .docx'dan olingan HTML
// (rasmlar bilan, mavjud bitta faylli import — courseDetail.js#importDocxFile
// — bilan bir xil mexanizm, faqat ko'p faylga kengaytirilgan). "xlsxFileName" —
// SHU darsga mos test fayli nomi (agar .docx bilan bir xil nomli .xlsx
// tanlangan bo'lsa) — backend shu nom orqali multipart'dagi haqiqiy
// faylni topadi; topilmasa (yoki bo'sh) — dars TESTSIZ yaratiladi.
public record LessonImportItemDto(
        String title,
        String html,
        String xlsxFileName
) {
}
