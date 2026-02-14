package behzoddev.testproject.dto.testsession;

import behzoddev.testproject.dto.question.QuestionDto;

import java.util.List;

public record StartTestResponseDto(Long testSessionId,
                                   List<QuestionDto> questions) {
}
