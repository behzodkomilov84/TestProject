package behzoddev.testproject.dto.course;

// Kurs Bo'limi (CourseChapter) — id/nom/tartib + shu Bo'limda nechta
// mavzu (CourseSection) borligi. courseDetail.js'dagi Bo'lim tanlash
// select'ini TO'LIQ ro'yxat bilan (hatto hozircha bo'sh — hech qanday
// mavzuga biriktirilmagan — bo'limlar bilan ham) to'ldirish uchun, shu
// orqali bo'sh Bo'limlarni o'chirish imkoniyati ham beriladi
// (CourseController.deleteChapter — faqat sectionCount==0 bo'lsa ruxsat
// etiladi).
public record CourseChapterDto(Long id, String name, int orderIndex, long sectionCount) {
}
