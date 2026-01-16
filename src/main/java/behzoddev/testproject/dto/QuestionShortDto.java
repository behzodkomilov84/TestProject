package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record QuestionShortDto(
        @NotBlank(message = "QuestionText must not be blank") String questionText,
        List<AnswerShortDto> answers) {
}
