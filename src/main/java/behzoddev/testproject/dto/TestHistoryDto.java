package behzoddev.testproject.dto;

import java.time.LocalDateTime;

public record TestHistoryDto(
        Long testSessionId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalQuestions,
        int correctAnswers,
        int wrongAnswers,
        int percent,
        long durationSec
) {}

