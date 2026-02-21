package behzoddev.testproject.dto.student;

import behzoddev.testproject.entity.enums.TaskStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ResponseAssignmentsAndTaskStatusDto(
        Long id,
        Long questionSetId,
        String questionSetName,
        Long groupId,
        String groupName,
        Long assignerId,
        String assignerName,
        LocalDateTime assignedAt,
        LocalDateTime dueDate,
        TaskStatus taskStatus
) {
}
