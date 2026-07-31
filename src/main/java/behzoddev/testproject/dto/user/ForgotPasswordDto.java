package behzoddev.testproject.dto.user;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordDto(
        @NotBlank(message = "❌Username bo'sh bo'lishi mumkin emas.") String username
) {
}
