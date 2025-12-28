package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record ScienceNameDto(@NotBlank(message = "Science name must not be blank") String name) {
}
