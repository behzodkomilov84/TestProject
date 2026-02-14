package behzoddev.testproject.dto.testsession;

import java.util.List;

public record FinishTestRequestDto(
        Long testSessionId,
        Long startedAt,
        Long finishedAt,
        List<AnswerResultDto> answers
) {}

