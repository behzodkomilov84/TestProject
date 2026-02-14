package behzoddev.testproject.dto.student;

import java.util.List;

public record ResponseQuestionSetDto(
        Long id,
        String name,
        List<ResponseQuestionDto> questions
) {
}
