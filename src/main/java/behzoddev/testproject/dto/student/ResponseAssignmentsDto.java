package behzoddev.testproject.dto.student;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ResponseAssignmentsDto(
        Long id,
        Long questionSetId,
        String questionSetName,
        Long groupId,
        String groupName,
        Long assignerId,
        String assignerName,
        LocalDateTime assignedAt,
        LocalDateTime dueDate
) {
}
