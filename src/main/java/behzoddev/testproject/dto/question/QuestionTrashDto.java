package behzoddev.testproject.dto.question;

import java.time.LocalDateTime;

// "O'chirilganlar savati" (savol/test darajasida) — soft-delete qilingan
// bitta savol (QuestionService.getDeletedQuestions).
public record QuestionTrashDto(Long id, String questionText, LocalDateTime deletedAt) {
}
