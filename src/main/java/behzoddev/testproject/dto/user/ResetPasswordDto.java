package behzoddev.testproject.dto.user;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordDto(
        @NotBlank(message = "❌Username bo'sh bo'lishi mumkin emas.") String username,
        @NotBlank(message = "❌Kod bo'sh bo'lishi mumkin emas.") String code,
        @NotBlank(message = "❌Yangi parol bo'sh bo'lishi mumkin emas.") String newPassword,
        @NotBlank(message = "❌Parolni tasdiqlash bo'sh bo'lishi mumkin emas.") String confirmPassword
) {
}
