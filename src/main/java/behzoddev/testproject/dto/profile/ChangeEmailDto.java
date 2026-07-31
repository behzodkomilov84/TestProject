package behzoddev.testproject.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailDto(
        @NotBlank
        @Email
        String newEmail
) {
}
