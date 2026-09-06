package behzoddev.testproject.dto.course;

import java.time.LocalDateTime;

// "Forum" — kurs savollari ro'yxati (foydalanuvchi so'rovi, 2026-09-06).
// "authorIsCreator" — frontend'da "🎓 Kurs muallifi" belgisini
// ko'rsatish uchun (kurs.createdBy bilan solishtirib topiladi).
public record ForumThreadDto(
        Long id,
        String questionText,
        Long authorId,
        String authorName,
        boolean authorIsCreator,
        LocalDateTime createdAt,
        long replyCount
) {
}
