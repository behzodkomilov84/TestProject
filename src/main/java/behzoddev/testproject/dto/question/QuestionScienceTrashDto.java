package behzoddev.testproject.dto.question;

import java.time.LocalDateTime;

// "O'chirilganlar savati" — BUTUN FAN bo'yicha (barcha mavzular birga),
// topics.html'dagi global savol savati uchun (QuestionService.
// getDeletedQuestionsByScience). QuestionTrashDto'dan farqi — bu yerda
// har bir savol qaysi MAVZUga va qaysi BO'LIMga tegishli ekani ham bor
// (foydalanuvchi ro'yxatni ko'rib, aynan qaysi mavzudan ekanini bilishi
// uchun so'ralgan).
public record QuestionScienceTrashDto(
        Long id,
        String questionText,
        LocalDateTime deletedAt,
        Long topicId,
        String topicName,
        // Ixtiyoriy — mavzu biror Bo'limga biriktirilgan bo'lsa uning nomi
        // (masalan "I. UMUMIY KIMYO"). NULL — mavzu bo'limsiz.
        String sectionName) {
}
