package behzoddev.testproject.dto;

import java.util.List;

public record StartTestResponseDto(Long testSessionId,
                                   List<QuestionDto> questions) {
}
