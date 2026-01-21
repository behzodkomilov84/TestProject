package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record TopicIdAndNameDto(
        Long id,
        @NotBlank(message = "❌Topic.name bo'sh bo'lishi mumkin emas.") String name) {
}
