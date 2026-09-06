package behzoddev.testproject.dto.course;

import behzoddev.testproject.dto.question.QuestionDto;

// "Kurs bo'yicha barcha savollarni ko'rish" sahifasi (foydalanuvchi
// so'rovi, 2026-09-06: ADMIN o'zi yaratgan kursniki, OWNER barchasiniki
// ko'ra oladi — CourseService#requireManageableCourse orqali) — bitta
// savol qaysi DARSGA (CourseSection, linkedTopic orqali) tegishli
// ekanini ham ko'rsatish uchun QuestionDto'ni shu kontekst bilan o'raydi.
public record CourseQuestionDto(
        QuestionDto question,
        Long courseSectionId,
        String courseSectionTitle,
        // "👁️ Ko'rish"/"✏️ Tahrirlash" tugmalari uchun — question.js'ga
        // "?topicId=&focus=/edit=" orqali to'g'ridan-to'g'ri o'tkazish
        // (science.js#viewScienceSearchResult bilan bir xil g'oya).
        Long topicId
) {
}
