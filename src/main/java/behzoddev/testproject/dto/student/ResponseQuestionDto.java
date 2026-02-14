package behzoddev.testproject.dto.student;

import java.util.List;

public record ResponseQuestionDto(
        Long id,
        String text,
        List<ResponseAnswerDto> answers
) {
}
