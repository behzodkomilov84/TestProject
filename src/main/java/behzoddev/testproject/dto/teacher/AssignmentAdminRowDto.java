package behzoddev.testproject.dto.teacher;

import java.time.LocalDateTime;

public record AssignmentAdminRowDto(
        Long id,
        String questionSetName,
        String groupName,
        LocalDateTime assignedAt,
        LocalDateTime dueDate,
        Long totalStudents,
        Long finished,
        Double avgPercent
) {}

