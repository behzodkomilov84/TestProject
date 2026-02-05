package behzoddev.testproject.dto;

import java.util.List;

public record MaxRequestDto(List<Long> topicIds,
                            String testMode) {
}
