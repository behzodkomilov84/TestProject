package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder
public record QuestionSaveDto(Long topicId,
                              @NotBlank(message = "❌questionText bo'sh bo'lishi mumkin emas.") String questionText,
                              List<AnswerShortDto> answers) {
}
