package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerShortDto(
        @NotBlank(message = "AnswerText must not be blank") String answerText,
        Boolean isTrue) {}

