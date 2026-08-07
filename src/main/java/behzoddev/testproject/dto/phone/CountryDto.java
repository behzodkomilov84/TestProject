package behzoddev.testproject.dto.phone;

import lombok.Builder;

// Telefon raqam kiritishda davlat tanlash dropdown'i uchun.
@Builder
public record CountryDto(
        String isoCode,   // "UZ", "US", "RU" ...
        String name,      // "O'zbekiston", "United States" ...
        String dialCode   // "998", "1", "7" ...
) {
}
