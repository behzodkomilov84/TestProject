package behzoddev.testproject.dto.teacher;

import jakarta.validation.constraints.NotBlank;

public record GroupDto(
        Long id,
        @NotBlank(message = "❌Gruppa nomi bo'sh bo'lishi mumkin emas.") String name
) {}
