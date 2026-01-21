package behzoddev.testproject.dto.batch;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record ScienceCreateDto(@NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name) {
}
