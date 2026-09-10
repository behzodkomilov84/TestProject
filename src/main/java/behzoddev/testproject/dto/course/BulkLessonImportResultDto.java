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
        int sectionsWithTests,
        long questionsImported,
        List<String> warnings,
        List<String> errors
) {
}
