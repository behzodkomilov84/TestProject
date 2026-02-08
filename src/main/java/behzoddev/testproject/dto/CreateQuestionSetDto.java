package behzoddev.testproject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateQuestionSetDto(
        @NotBlank String name,
        @NotEmpty Set<Long> questionIds
) {}
