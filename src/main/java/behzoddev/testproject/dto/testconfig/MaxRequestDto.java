package behzoddev.testproject.dto.testconfig;

import java.util.List;

public record MaxRequestDto(List<Long> topicIds,
                            String testMode) {
}
