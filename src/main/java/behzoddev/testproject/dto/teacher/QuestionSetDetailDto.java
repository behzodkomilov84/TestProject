package behzoddev.testproject.dto.teacher;

import java.util.List;

// "📂 Tarkibini tahrirlash" — mavjud savollar to'plamini ochib, ICHIDAGI
// savollarni (matni bilan birga, faqat ID emas) "Tanlangan savollar"
// ro'yxatiga qayta yuklash uchun (teacher-builder.js).
public record QuestionSetDetailDto(Long id, String name, List<ResponseQuestionTextDto> questions) {
}
