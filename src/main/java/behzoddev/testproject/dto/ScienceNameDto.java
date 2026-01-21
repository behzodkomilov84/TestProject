package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record ScienceNameDto(@NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name) {
}
