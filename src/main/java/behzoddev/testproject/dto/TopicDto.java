package behzoddev.testproject.dto;

import java.util.Set;

public record TopicDto(Long id, String name, Set<QuestionDto> questions) {
}
