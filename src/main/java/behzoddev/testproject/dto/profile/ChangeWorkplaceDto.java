package behzoddev.testproject.dto.profile;

import jakarta.validation.constraints.NotBlank;

public record ChangeWorkplaceDto(@NotBlank String workplace) {
}
