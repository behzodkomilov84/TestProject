package behzoddev.testproject.dto.question;

import behzoddev.testproject.dto.answer.AnswerShortDto;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record QuestionShortDto(
        @NotBlank(message = "❌questionText bo'sh bo'lishi mumkin emas.") String questionText,
        List<AnswerShortDto> answers) {
}
