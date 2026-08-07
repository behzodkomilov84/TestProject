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
        List<String> roles
) {}
