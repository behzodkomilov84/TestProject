package behzoddev.testproject.dto.student;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AttemptDto(
        Long attemptId,
        Integer totalQuestions,
        Integer correctAnswers,
        Integer percent,
        Integer durationSec,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime lastSync
) {
}

