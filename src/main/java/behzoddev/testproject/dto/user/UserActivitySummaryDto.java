package behzoddev.testproject.dto.user;

import lombok.Builder;

import java.util.List;

// "/users" sahifasidagi "Oxirgi tashrif vaqti" katakchasi bosilganda
// ochiladigan oyna uchun (foydalanuvchi so'rovi, 2026-09-10): "jami
// necha soat saytdan foydalandi" + kurslar kesimidagi taqsimot.
@Builder
public record UserActivitySummaryDto(
        long totalSeconds,
        List<CourseActivityDto> courseBreakdown
) {
}
