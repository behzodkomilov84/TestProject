package behzoddev.testproject.dto;

import java.util.List;

public record TestQuestionDto(Long id, String questionText, List<AnswerIdAndTextDto> answers) {
}
