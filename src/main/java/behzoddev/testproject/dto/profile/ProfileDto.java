package behzoddev.testproject.dto.profile;

import java.util.List;

public record ProfileDto(
        Long id,
        String username,
        String email,
        // Tahrirlash formasini oldindan to'ldirish uchun — xom E.164 ("+998901234567"),
        // davlat kodi ("UZ") va davlat kodisiz milliy qism ("901234567").
        String phoneNumber,
        String phoneNumberFormatted,
        String phoneCountryIso,
        String phoneNationalNumber,
        List<String> roles,
        // Profilga qo'shimcha ma'lumotlar (foydalanuvchi so'rovi, 2026-09-06).
        String firstName,
        String lastName,
        String workplace,
        String jobTitle,
        String avatarUrl,
        boolean telegramConnected,
        // Telegram'ning o'zi ko'rsatadigan @username — "qaysi Telegram
        // hisobiga bog'langan" degan savolga inson TANIY oladigan javob
        // (foydalanuvchi so'rovi, 2026-09-06). NULL bo'lishi mumkin
        // (Telegram'da username ixtiyoriy).
        String telegramUsername
) {}
