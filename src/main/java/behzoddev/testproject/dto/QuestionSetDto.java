package behzoddev.testproject.dto;

import java.util.List;

public record QuestionSetDto(
        Long id,
        String name,
        List<Long> questionIds
) {}
