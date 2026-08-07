package behzoddev.testproject.service;

import behzoddev.testproject.dto.phone.CountryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Google libphonenumber ustidagi ingichka wrapper — mock kerak emas,
 * to'g'ridan-to'g'ri haqiqiy kutubxona bilan tekshiramiz.
 */
class PhoneNumberServiceTest {

    private PhoneNumberService phoneNumberService;

    @BeforeEach
    void setUp() {
        phoneNumberService = new PhoneNumberService();
    }

    @Test
    void normalize_validUzNumber_returnsE164() {
        String result = phoneNumberService.normalize("UZ", "901234567");
        assertThat(result).isEqualTo("+998901234567");
    }

    @Test
    void normalize_blankIsoCode_defaultsToUz() {
        String result = phoneNumberService.normalize("", "901234567");
        assertThat(result).isEqualTo("+998901234567");
    }

    @Test
    void normalize_blankNumber_throws() {
        assertThatThrownBy(() -> phoneNumberService.normalize("UZ", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bo'sh bo'lishi mumkin emas");
    }

    @Test
    void normalize_invalidNumberForRegion_throws() {
        assertThatThrownBy(() -> phoneNumberService.normalize("UZ", "123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalize_garbageInput_throwsFormatError() {
        assertThatThrownBy(() -> phoneNumberService.normalize("UZ", "not-a-number-at-all"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatForDisplay_validE164_returnsInternationalFormat() {
        String result = phoneNumberService.formatForDisplay("+998901234567");
        assertThat(result).isEqualTo("+998 90 123 45 67");
    }

    @Test
    void formatForDisplay_blank_returnsNull() {
        assertThat(phoneNumberService.formatForDisplay(null)).isNull();
        assertThat(phoneNumberService.formatForDisplay(" ")).isNull();
    }

    @Test
    void formatForDisplay_unparsable_returnsInputAsIs() {
        String result = phoneNumberService.formatForDisplay("not-a-number");
        assertThat(result).isEqualTo("not-a-number");
    }

    @Test
    void regionOf_returnsCorrectCountryCode() {
        assertThat(phoneNumberService.regionOf("+998901234567")).isEqualTo("UZ");
        assertThat(phoneNumberService.regionOf("+14155552671")).isEqualTo("US");
    }

    @Test
    void regionOf_blank_defaultsToUz() {
        assertThat(phoneNumberService.regionOf(null)).isEqualTo("UZ");
        assertThat(phoneNumberService.regionOf("")).isEqualTo("UZ");
    }

    @Test
    void nationalNumberOf_returnsNationalPartWithoutCountryCode() {
        assertThat(phoneNumberService.nationalNumberOf("+998901234567")).isEqualTo("901234567");
    }

    @Test
    void nationalNumberOf_blank_returnsEmptyString() {
        assertThat(phoneNumberService.nationalNumberOf(null)).isEmpty();
    }

    @Test
    void listCountries_uzbekistanListedFirst() {
        List<CountryDto> countries = phoneNumberService.listCountries();

        assertThat(countries).isNotEmpty();
        assertThat(countries.get(0).isoCode()).isEqualTo("UZ");
        assertThat(countries.get(0).dialCode()).isEqualTo("998");
    }
}
