package behzoddev.testproject.dto.student;

import java.util.List;

public record SyncAttemptRequestDto(
        Long attemptId,
        List<AnswerSyncDto> answers
) {
}
