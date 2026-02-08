package behzoddev.testproject.dto;

// Ответ
public record QuestionSetResponseDto(
        Long id,
        String name,
        int questionCount
) {}
