package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

public record TopicNameDto(@NotBlank(message = "Topic name must not be blank") String name) {}
