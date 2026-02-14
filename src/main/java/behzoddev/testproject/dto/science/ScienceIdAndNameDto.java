package behzoddev.testproject.dto.science;

import jakarta.validation.constraints.NotBlank;

public record ScienceIdAndNameDto(
        Long id,
        @NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name) {
}
