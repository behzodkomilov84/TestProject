package behzoddev.testproject.dto.course;

import java.math.BigDecimal;

// OWNER kursga obunani qo'lda qayd qilib, darhol tasdiqlaydi.
// durationMonths null bo'lsa — standart 1 oy (CourseSubscriptionService'ga qarang).
public record CreateCourseSubscriptionDto(Long userId, BigDecimal amount, Integer durationMonths, String note) {
}
