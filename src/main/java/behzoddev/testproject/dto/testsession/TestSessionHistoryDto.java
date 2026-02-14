package behzoddev.testproject.dto.testsession;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record TestSessionHistoryDto(
        Long testSessionId,
        String scienceName,
        int totalQuestions,
        int correctAnswers,
        int percent,
        LocalDateTime finishedAt,
        Long durationSec
) {}

