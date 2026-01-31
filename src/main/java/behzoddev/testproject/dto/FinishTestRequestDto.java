package behzoddev.testproject.dto;

import java.util.List;

public record FinishTestRequestDto(
        Long testSessionId,
        Long startedAt,
        Long finishedAt,
        List<AnswerResultDto> answers
) {}

