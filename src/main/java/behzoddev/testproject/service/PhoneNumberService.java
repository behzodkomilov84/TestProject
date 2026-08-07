package behzoddev.testproject.service;

import behzoddev.testproject.dto.phone.CountryDto;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// Telefon raqamlarni istalgan davlat uchun tekshirish/normallashtirish —
// Google'ning libphonenumber kutubxonasi orqali (barcha davlatlarning
// formatlari, uzunligi va h.k. haqidagi ma'lumot shu kutubxona ichida
// tayyor, qo'lda ro'yxat yuritish shart emas — yangi davlat "qo'shish"
// kerak bo'lmaydi, ular allaqachon qo'llab-quvvatlanadi).
@Slf4j
@Service
public class PhoneNumberService {

    private static final String DEFAULT_REGION = "UZ";

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    // Dropdown uchun — O'zbekiston ro'yxat boshida, qolgani nomi bo'yicha alifbo tartibida.
    public List<CountryDto> listCountries() {
        return phoneNumberUtil.getSupportedRegions().stream()
                .map(this::toCountryDto)
                .sorted(Comparator
                        .comparing((CountryDto c) -> !c.isoCode().equals(DEFAULT_REGION))
                        .thenComparing(CountryDto::name))
                .toList();
    }

    private CountryDto toCountryDto(String isoCode) {
        String name = new Locale.Builder().setRegion(isoCode).build().getDisplayCountry(new Locale("uz"));
        int dialCode = phoneNumberUtil.getCountryCodeForRegion(isoCode);

        return CountryDto.builder()
                .isoCode(isoCode)
                .name(name)
                .dialCode(String.valueOf(dialCode))
                .build();
    }

    // Tekshiradi va E.164 formatga ("+998901234567") o'giradi — DB'da
    // shu ko'rinishda saqlanadi. Noto'g'ri bo'lsa aniq xabar bilan otadi.
    public String normalize(String isoCode, String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            throw new IllegalArgumentException("❌Telefon raqam bo'sh bo'lishi mumkin emas.");
        }

        String region = (isoCode == null || isoCode.isBlank()) ? DEFAULT_REGION : isoCode.toUpperCase();

        try {
            PhoneNumber parsed = phoneNumberUtil.parse(rawNumber, region);

            if (!phoneNumberUtil.isValidNumber(parsed)) {
                throw new IllegalArgumentException("❌Telefon raqam noto'g'ri yoki tanlangan davlat uchun mos emas.");
            }

            return phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new IllegalArgumentException("❌Telefon raqam formati noto'g'ri.");
        }
    }

    // Ko'rsatish uchun chiroyli xalqaro format: "+998 90 123 45 67".
    public String formatForDisplay(String e164Number) {
        if (e164Number == null || e164Number.isBlank()) return null;

        try {
            PhoneNumber parsed = phoneNumberUtil.parse(e164Number, null);
            return phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException e) {
            log.warn("Saqlangan telefon raqamni formatlab bo'lmadi: {}", e164Number, e);
            return e164Number;
        }
    }

    // Tahrirlash formasini oldindan to'ldirish uchun — E.164'dan davlat kodini
    // ajratib oladi (masalan "+998901234567" -> "UZ").
    public String regionOf(String e164Number) {
        if (e164Number == null || e164Number.isBlank()) return DEFAULT_REGION;

        try {
            PhoneNumber parsed = phoneNumberUtil.parse(e164Number, null);
            String region = phoneNumberUtil.getRegionCodeForNumber(parsed);
            return region != null ? region : DEFAULT_REGION;
        } catch (NumberParseException e) {
            return DEFAULT_REGION;
        }
    }

    // Tahrirlash formasidagi "mahalliy raqam" maydonini to'ldirish uchun —
    // davlat kodisiz milliy qism (masalan "+998901234567" -> "901234567").
    public String nationalNumberOf(String e164Number) {
        if (e164Number == null || e164Number.isBlank()) return "";

        try {
            PhoneNumber parsed = phoneNumberUtil.parse(e164Number, null);
            return String.valueOf(parsed.getNationalNumber());
        } catch (NumberParseException e) {
            return "";
        }
    }
}
