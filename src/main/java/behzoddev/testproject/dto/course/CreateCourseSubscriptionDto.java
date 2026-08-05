package behzoddev.testproject.dto.course;

import java.math.BigDecimal;

// OWNER kursga obunani qo'lda qayd qilib, darhol tasdiqlaydi.
public record CreateCourseSubscriptionDto(Long userId, BigDecimal amount, String note) {
}
