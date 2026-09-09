package behzoddev.testproject.dto.user;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

// Foydalanuvchi so'rovi, 2026-09-06: "users jadvalidagi barcha ustunlarni
// Foydalanuvchilar sahifasiga chiqar" — Telegram/telefon dublikat
// muammosini debug qilish uchun OWNER'ga barcha muhim ma'lumotlar
// (parol/hash'dan tashqari) admin jadvalida ko'rinishi kerak edi.
@Builder
public record UserDto(
        Long id,
        String username,
        List<String> roles,
        boolean locked,
        String email,
        String phoneNumber,
        Long telegramId,
        String telegramUsername,
        String googleId,
        String facebookId,
        String firstName,
        String lastName,
        String workplace,
        String jobTitle,
        // Avatar rasmi — /users sahifasida ko'rsatish uchun (foydalanuvchi
        // so'rovi, 2026-09-07: "фойдаланувчиларнинг аватарини ҳам қўш").
        String avatarUrl,
        // "Ro'yxatdan o'tgan sana" ustuni (foydalanuvchi so'rovi,
        // 2026-09-09). Eski hisoblarda null bo'lishi mumkin.
        LocalDateTime createdAt
) {
}
