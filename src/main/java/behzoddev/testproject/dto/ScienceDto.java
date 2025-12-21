package behzoddev.testproject.dto;

import java.util.Set;

public record ScienceDto(Long id, String name, Set<TopicDto> topics) {

}
