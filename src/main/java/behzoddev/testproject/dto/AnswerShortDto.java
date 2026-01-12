package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record AnswerShortDto(
        @NotBlank(message = "AnswerText must not be blank") String answerText,
        @NotBlank(message = "isTrue field must not be blank") Boolean isTrue,
        String commentary) {

    @Override
    public String answerText() {
        return answerText;
    }

    @Override
    public Boolean isTrue() {
        return isTrue;
    }
}

