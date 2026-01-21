package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginDto(@NotBlank(message = "❌Username bo'sh bo'lishi mumkin emas.") String username,
                       @NotBlank(message = "❌Password bo'sh bo'lishi mumkin emas.") String password) {
}
