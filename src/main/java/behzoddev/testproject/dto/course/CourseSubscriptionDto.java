package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record CourseSubscriptionDto(
        Long id,
        Long userId,
        String username,
        Long courseId,
        String courseTitle,
        BigDecimal amount,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String note,
        LocalDateTime createdAt,
        // Qanday yo'l bilan berilgani — "MANUAL" (OWNER/ADMIN qo'lda),
        // "ONLINE" (Click orqali avtomatik) yoki "TRIAL" (bepul sinov
        // bonusi). Umumiy SubscriptionDto'dagi "source" enum'iga o'xshab,
        // lekin CourseSubscription'da alohida ustun yo'q — mavjud
        // confirmedBy/trial maydonlaridan hisoblanadi (foydalanuvchi
        // so'rovi, 2026-09-09: "қайси усулда обуна берилганини қўшиш
        // керак: автоматик... ёки қўлдами?").
        String source
) {
}
