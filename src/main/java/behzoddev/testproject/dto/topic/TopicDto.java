package behzoddev.testproject.dto.topic;

import behzoddev.testproject.dto.question.QuestionDto;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record TopicDto(
        Long id,
        @NotBlank(message = "❌Topic.name bo'sh bo'lishi mumkin emas.") String name,
        Set<QuestionDto> questions) {
}
