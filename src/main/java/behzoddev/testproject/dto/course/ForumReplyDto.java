package behzoddev.testproject.dto.course;

import java.time.LocalDateTime;

// "Forum" — bitta savolga javoblar ro'yxati (foydalanuvchi so'rovi,
// 2026-09-06). XOHLAGAN foydalanuvchi javob yozgan bo'lishi mumkin,
// "authorIsCreator" faqat KO'RSATISH uchun (kim javob berayotganini
// ajratish, cheklov emas).
public record ForumReplyDto(
        Long id,
        String replyText,
        Long authorId,
        String authorName,
        boolean authorIsCreator,
        LocalDateTime createdAt
) {
}
