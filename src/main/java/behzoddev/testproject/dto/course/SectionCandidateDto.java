package behzoddev.testproject.dto.course;

// Bulk-import paytida bitta kod (masalan "0002") bir nechta darsga mos
// kelib qolganda (AmbiguousTestFileDto#candidates) — foydalanuvchi qaysi
// biriga tegishli ekanini TANLASHI uchun ko'rsatiladigan nomzod dars
// (foydalanuvchi so'rovi, 2026-09-12: "qaysi darslar nomlariga mos kelib
// qolayotganini darhol ro'yxatini bersin, shu yerda to'g'ri variantni
// belgilasin").
public record SectionCandidateDto(
        Long sectionId,
        String title
) {
}
