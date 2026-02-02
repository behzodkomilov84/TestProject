package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDto(
        @NotBlank(message = "❌Username bo'sh bo'lishi mumkin emas.") String username,
        @NotBlank(message = "❌Password bo'sh bo'lishi mumkin emas.") @Size(min = 6, message = "Parolingiz kamida 6 xonali bo'lishi kerak") String password,
        @NotBlank(message = "❌ConfirmPassword bo'sh bo'lishi mumkin emas.") String confirmPassword) {

}
