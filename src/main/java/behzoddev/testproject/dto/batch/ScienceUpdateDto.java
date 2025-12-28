package behzoddev.testproject.dto.batch;

import jakarta.validation.constraints.NotBlank;

public record ScienceUpdateDto(Long id,
                               @NotBlank(message = "Science name must not be blank") String name) {
}
