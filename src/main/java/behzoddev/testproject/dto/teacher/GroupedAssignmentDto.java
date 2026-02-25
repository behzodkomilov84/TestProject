package behzoddev.testproject.dto.teacher;

import java.time.LocalDateTime;

public record GroupedAssignmentDto(
        Long id,
        Long setId,
        Long groupId,
        Long assignedBy,
        LocalDateTime assignedAt,
        LocalDateTime dueDate,
        Long totalStudents
) {}
