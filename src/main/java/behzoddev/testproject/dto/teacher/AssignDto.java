package behzoddev.testproject.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;

public record AssignDto(
        Long groupId,
        Long setId,
        LocalDateTime dueDate,
        List<Long> studentIds) {}

