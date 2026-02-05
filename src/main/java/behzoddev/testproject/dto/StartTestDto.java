package behzoddev.testproject.dto;

import java.util.List;

public record StartTestDto(List<Long> topicIds, int limit, String mode) {
}
