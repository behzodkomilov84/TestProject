package behzoddev.testproject.dto;

import java.time.LocalDateTime;

public record TestSessionHistoryDto(
        Long id,
        int totalQuestions,
        int correctAnswers,
        int percent,
        LocalDateTime finishedAt,
        Long durationSec
) {}

