package behzoddev.testproject.dto.batch;

import jakarta.validation.constraints.NotBlank;

public record ScienceUpdateDto(Long id,
                               @NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name) {
}
