package behzoddev.testproject.dto;

public record TestStatsDto(
        int totalTests,
        int avgPercent,
        int bestPercent,
        int worstPercent,
        long totalDurationSec
) {}

