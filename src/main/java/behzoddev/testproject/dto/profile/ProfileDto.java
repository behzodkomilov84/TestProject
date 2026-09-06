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
        // Telegram'ning o'zi (username/id) emas, faqat "bog'langan/
        // bog'lanmagan" holati ko'rsatiladi — profile.js shunga qarab
        // status chip'ini chizadi.
        boolean telegramConnected
) {}
