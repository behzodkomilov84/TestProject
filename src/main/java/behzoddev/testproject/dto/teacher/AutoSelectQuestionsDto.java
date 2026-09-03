package behzoddev.testproject.dto.teacher;

import java.util.List;

// "🎲 Avtomatik tanlash" (teacher-builder.js, "Savollar to'plami"
// sahifasi) — tanlangan mavzular (topicIds) orasidan, HAR BIRIGA TENG
// bo'lib (savollar soniga qarab EMAS), jami "totalCount" ta savolni
// tasodifiy tanlab berish so'rovi.
public record AutoSelectQuestionsDto(List<Long> topicIds, int totalCount) {
}
