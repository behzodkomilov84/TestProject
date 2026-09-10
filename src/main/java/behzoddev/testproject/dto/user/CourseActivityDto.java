package behzoddev.testproject.dto.user;

import lombok.Builder;

// "/users" sahifasidagi "Oxirgi tashrif" oynasida — bitta kursga
// aloqador sahifalarda o'tkazilgan vaqt (foydalanuvchi so'rovi,
// 2026-09-10).
@Builder
public record CourseActivityDto(
        Long courseId,
        String courseTitle,
        long totalSeconds
) {
}
