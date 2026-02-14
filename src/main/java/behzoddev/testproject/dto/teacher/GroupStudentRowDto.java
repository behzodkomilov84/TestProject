package behzoddev.testproject.dto.teacher;

public record GroupStudentRowDto(
        Long inviteId,
        Long pupilId,
        String username,
        String status
) {}

