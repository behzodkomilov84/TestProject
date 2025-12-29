package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record TopicShortDto(@NotBlank(message = "Id must not be blank") Long id,
                            @NotBlank(message = "Topic name must not be blank") String name,
                            @NotBlank(message = "ScienceId must not be blank") Long scienceId) {
}
