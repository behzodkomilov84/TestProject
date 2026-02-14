package behzoddev.testproject.dto.testsession;

public record TestStatsDto(
        int totalTests,
        int avgPercent,
        int bestPercent,
        int worstPercent,
        long totalDurationSec
) {}

