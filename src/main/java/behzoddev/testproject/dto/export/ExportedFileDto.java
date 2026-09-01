package behzoddev.testproject.dto.export;

// Excel/Word/variant eksportlari (ExcelService/WordService/ExamVariantService)
// uchun umumiy natija — bayt massivi + fayl nomi uchun taklif etilgan
// asos (masalan "savollar_Bakteriologiya", kengaytmasiz — kontroller
// o'zi ".xlsx"/".docx"/".zip" qo'shadi). Nomi mavzu/bo'lim/fan NOMIDAN
// olinadi (raqamli ID emas) — foydalanuvchi diskda faylni ko'rganda
// darhol qaysi mavzuga tegishli ekanini bilishi uchun.
public record ExportedFileDto(byte[] data, String filenameBase) {
}
