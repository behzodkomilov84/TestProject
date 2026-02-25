package behzoddev.testproject.dto.teacher;

import java.time.LocalDateTime;

public record AssignmentStudentDetailDto(
        Long pupilId,
        String pupilName,
        String status,
        Integer percent,
        Integer durationSec,
        LocalDateTime lastActivity
) {
}
