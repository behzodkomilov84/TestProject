package behzoddev.testproject.dto.excel;

import java.util.List;

public record ImportResultDto(
        boolean success,
        Long imported,
        List<String> errors) {
}
