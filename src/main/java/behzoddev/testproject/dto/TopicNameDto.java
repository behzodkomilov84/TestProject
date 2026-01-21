package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record TopicNameDto(@NotBlank(message = "❌Topic.name bo'sh bo'lishi mumkin emas.") String name) {
}
