package behzoddev.testproject.dto.science;

import behzoddev.testproject.dto.topic.TopicDto;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record ScienceDto(
        Long id,
        @NotBlank(message = "❌Fan nomi bo'sh bo'lishi mumkin emas.") String name,
        Set<TopicDto> topics) {
}
