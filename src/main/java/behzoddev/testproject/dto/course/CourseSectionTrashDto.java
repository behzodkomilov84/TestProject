package behzoddev.testproject.dto.course;

import java.time.LocalDateTime;

// "O'chirilganlar savati" (kurs ichida) — soft-delete qilingan bitta kurs
// mavzusi/darsi (CourseService.getDeletedSections). "chapterId"/"chapterName"
// — foydalanuvchi so'rovi, 2026-09-12: "o'chirilgan darslar modalida
// darslarni ham gruppalashtir" — o'chirilgandan keyin ham CourseSection'ning
// "chapter" bog'lanishi TEGILMAY qoladi (faqat deletedAt o'rnatiladi), shu
// sabab har bir dars ASL Mavzusi bo'yicha guruhlanadi (chapterId == null —
// "— Mavzusiz —" guruhi).
public record CourseSectionTrashDto(Long id, String title, LocalDateTime deletedAt, Long chapterId, String chapterName) {
}
