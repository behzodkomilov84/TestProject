package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record ScienceDto(
        Long id,
        @NotBlank(message = "Science name must not be blank") String name,
        Set<TopicDto> topics) {}
