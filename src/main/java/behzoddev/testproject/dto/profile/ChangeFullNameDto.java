package behzoddev.testproject.dto.profile;

import jakarta.validation.constraints.NotBlank;

public record ChangeFullNameDto(
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
