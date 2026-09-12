package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.util.List;

// "Mavzu" kartochkasidagi paketli (bulk) import natijasi — foydalanuvchi
// so'rovi, 2026-09-10: "import natijasi e'lon qilinadi statistikasi
// bilan". Import qisman muvaffaqiyatsiz bo'lsa ham (masalan bitta fayl
// buzuq) — qolganlari baribir yaratiladi, shu sabab bu DTO har doim
// 200 (OK) bilan qaytadi, muvaffaqiyat/xatolik "warnings"/"errors"
// ro'yxatlari orqali ko'rinadi.
@Builder
public record BulkLessonImportResultDto(
        int sectionsCreated,
        // Butun kursda (istalgan Mavzuda) shu nomli dars ALLAQACHON mavjud
        // bo'lgani uchun o'tkazib yuborilgan darslar soni — qayta import
        // ikki nusxa yaratmasligi uchun (foydalanuvchi so'rovi, 2026-09-10).
        int sectionsSkipped,
        int sectionsWithTests,
        long questionsImported,
        List<String> warnings,
        List<String> errors,
        // Fayl nomi ("kod") bir nechta darsga mos kelib qolgan hollar —
        // frontend shu ro'yxat asosida "to'g'risini tanlang" oynasini
        // ko'rsatadi (foydalanuvchi so'rovi, 2026-09-12). Eski frontend
        // buni e'tiborsiz qoldirsa ham, "errors" ro'yxatida odatdagi
        // tushunarli xabar sifatida allaqachon bor.
        List<AmbiguousTestFileDto> ambiguousXlsx
) {
}
