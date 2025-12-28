package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterDto(
        @NotBlank(message = "Username must not be blank") String username,
        @NotBlank(message = "Password must not be blank") String password,
        @NotBlank(message = "ConfirmPassword must not be blank") String confirmPassword) {

}
