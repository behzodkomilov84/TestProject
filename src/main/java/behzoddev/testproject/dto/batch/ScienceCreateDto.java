package behzoddev.testproject.dto.batch;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record ScienceCreateDto(@NotBlank(message = "Science name must not be blank") String name) {
}
