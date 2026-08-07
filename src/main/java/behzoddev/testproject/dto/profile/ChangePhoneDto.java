package behzoddev.testproject.dto.profile;

// isoCode — tanlangan davlat (masalan "UZ", "US"), rawNumber — foydalanuvchi
// kiritgan mahalliy raqam (davlat kodisiz). PhoneNumberService shu ikkisidan
// E.164 formatni tuzadi.
public record ChangePhoneDto(String isoCode, String rawNumber) {
}
