package behzoddev.testproject.dto.feedback;

import java.time.LocalDateTime;

// "Fikr va takliflar" ro'yxati (foydalanuvchi so'rovi, 2026-09-06).
// "authorIsStaff" — frontend'da "🛡️ Jamoa" belgisini ko'rsatish uchun
// (muallif OWNER yoki ADMIN bo'lsa).
public record FeedbackThreadDto(
        Long id,
        String feedbackText,
        Long authorId,
        String authorName,
        boolean authorIsStaff,
        String status,
        LocalDateTime createdAt,
        long replyCount
) {
}
