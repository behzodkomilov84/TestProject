package behzoddev.testproject.dto.profile;

import jakarta.validation.constraints.NotBlank;

public record ChangeJobTitleDto(@NotBlank String jobTitle) {
}
