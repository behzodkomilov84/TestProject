package behzoddev.testproject.dto.student;

import java.time.LocalDateTime;
import java.util.List;

public record AttemptFullDto(
        Long attemptId,
        Integer totalQuestions,
        Integer correctAnswers,
        Integer percent,
        Integer durationSec,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime lastSync,
        List<ResponseQuestionDto> questions,
        List<AttemptQuestionDto> attemptedQuestions
) {
}
