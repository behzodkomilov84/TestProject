package behzoddev.testproject.dto.student;

import java.time.LocalDateTime;
import java.util.List;

public record AttemptStartResponseDto(
        Long attemptId,
        LocalDateTime startedAt,
        List<ResponseQuestionDto> questions
) {
}
