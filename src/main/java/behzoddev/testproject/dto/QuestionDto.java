package behzoddev.testproject.dto;

import java.util.List;

public record QuestionDto(Long id, String questionText, List<AnswerDto> answers) {
}
