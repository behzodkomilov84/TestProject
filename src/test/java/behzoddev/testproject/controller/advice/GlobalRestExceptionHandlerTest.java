package behzoddev.testproject.controller.advice;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Haqiqiy production bug: kursni o'chirishga urinilganda chiqqan xom SQL
 * xatosi ("could not execute statement [Cannot delete or update a parent
 * row...]") foydalanuvchiga to'g'ridan-to'g'ri ko'rsatilib qolgan edi —
 * endi DataIntegrityViolationException tushunarli xabar bilan 409'ga
 * aylantiriladi.
 */
class GlobalRestExceptionHandlerTest {

    private final GlobalRestExceptionHandler handler = new GlobalRestExceptionHandler();

    @Test
    void handleDataIntegrityViolation_returnsConflictWithFriendlyMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Cannot delete or update a parent row: "
                        + "a foreign key constraint fails]");

        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsKey("error");
        // Xom SQL matni emas, tushunarli xabar bo'lishi kerak.
        assertThat(response.getBody().get("error")).doesNotContain("could not execute statement");
    }

    // Haqiqiy production bug: bo'lim nomi ustun uzunligidan (avval 200
    // belgi) oshib ketganda "Data truncation" xatosi chiqardi, lekin
    // FK xatolariga mo'ljallangan "bog'liq ma'lumotlar mavjud" xabari
    // bilan chalg'ituvchi tarzda qaytarilardi.
    @Test
    void handleDataIntegrityViolation_dataTruncation_returnsBadRequestWithAccurateMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Data truncation: Data too long for column 'title' at row 1]");

        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).doesNotContain("bog'liq ma'lumotlar");
        assertThat(response.getBody().get("error")).contains("uzun");
    }

    @Test
    void handleAccessDenied_returnsForbidden() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAccessDenied(new AccessDeniedException("⛔ Ruxsat yo'q"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("error")).isEqualTo("⛔ Ruxsat yo'q");
    }

    @Test
    void handleNotFound_returns404WithEmptyBody() {
        ResponseEntity<Void> response = handler.handleNotFound(new NoSuchElementException("topilmadi"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleResponseStatus_preservesStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "band");

        ResponseEntity<Map<String, String>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error")).isEqualTo("band");
    }

    @Test
    void handleAnyException_returnsBadRequestWithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAnyException(new IllegalArgumentException("❌noto'g'ri"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("❌noto'g'ri");
    }
}
