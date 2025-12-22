package behzoddev.testproject.dto;

import java.util.List;

public record QuestionShortDto(String questionText, List<AnswerShortDto> answers) {
}
