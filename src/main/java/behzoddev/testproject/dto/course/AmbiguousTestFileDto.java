package behzoddev.testproject.dto.course;

import java.util.List;

// Bulk-import'da yolg'iz .xlsx (mos .docx SHU importda yo'q) fayl nomi
// ("code") bir nechta darsning nomi bilan BOSHLANGAN holatda — tizim
// taxmin qilmasdan, foydalanuvchiga nomzodlar ro'yxatini ko'rsatib,
// TO'G'RISINI tanlashini so'raydi (foydalanuvchi so'rovi, 2026-09-12).
// Frontend (courseDetail.js) shu ro'yxat asosida tanlov oynasi ko'rsatadi,
// foydalanuvchi tanlagan "sectionId"ning ANIQ nomi bilan (title'ni to'liq
// mos qilib) xuddi shu xlsx faylni QAYTA yuboradi — bu safar
// findByCourse_IdAndTitleIgnoreCase ANIQ moslik topadi.
public record AmbiguousTestFileDto(
        String code,
        String xlsxFileName,
        List<SectionCandidateDto> candidates
) {
}
