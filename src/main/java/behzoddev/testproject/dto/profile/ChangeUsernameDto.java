package behzoddev.testproject.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUsernameDto(@NotBlank
                                @Size(min = 3, max = 50)
                                String newUsername) {
}
