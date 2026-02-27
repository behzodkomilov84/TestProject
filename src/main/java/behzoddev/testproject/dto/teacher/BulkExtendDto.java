package behzoddev.testproject.dto.teacher;

import java.util.List;

public record BulkExtendDto(
        List<Long> ids,
        String dueDate
) {
}
