package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerDto(Long id,
                        @NotBlank(message = "❌answerText bo'sh bo'lishi mumkin emas.") String answerText,
                        @NotBlank(message = "❌isTrue bo'sh bo'lishi mumkin emas.") boolean isTrue,
                        @NotBlank(message = "❌Izoh maydoni bo'sh bo'lishi mumkin emas.") String commentary) {
}
