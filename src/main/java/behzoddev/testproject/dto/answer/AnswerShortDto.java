package behzoddev.testproject.dto.answer;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record AnswerShortDto(
        @NotBlank(message = "❌answerText bo'sh bo'lishi mumkin emas.") String answerText,
        @NotBlank(message = "❌isTrue bo'sh bo'lishi mumkin emas.") Boolean isTrue,
        @NotBlank(message = "❌commentary bo'sh bo'lishi mumkin emas.") String commentary) {

}

