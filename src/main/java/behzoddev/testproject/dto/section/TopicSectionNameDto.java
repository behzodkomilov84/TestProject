package behzoddev.testproject.dto.section;

import jakarta.validation.constraints.NotBlank;

public record TopicSectionNameDto(
        @NotBlank(message = "❌Bo'lim nomi bo'sh bo'lishi mumkin emas.") String name) {
}
