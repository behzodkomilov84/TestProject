package behzoddev.testproject.dto.teacher;

import java.util.List;

public record QuestionSetDto(
        Long id,
        String name,
        List<Long> questionIds
) {}
