package behzoddev.testproject.controller.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Yagona global REST xato ishlovchisi.
 * <p>
 * Ilgari bu vazifani ikkita alohida @RestControllerAdvice bajarardi:
 * bittasi har qanday Exception'ni 400'ga aylantirar edi (shu jumladan
 * AccessDeniedException'ni ham — natijada frontend kutgan 403 status kodi
 * hech qachon kelmasdi), ikkinchisi esa faqat NoSuchElementException uchun
 * 404 qaytarardi. Ikkalasi shu yerga birlashtirildi.
 */
@Slf4j
@RestControllerAdvice
public class GlobalRestExceptionHandler {

    // Servis darajasida ResponseStatusException bilan otilgan xatolar —
    // status kodi va sababi shu bo'yicha saqlanadi (masalan 409 CONFLICT).
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", message));
    }

    // Ruxsat yo'q — 403 (ilgari bu ham umumiy 400'ga tushib ketardi)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    // Topilmadi — 404
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.notFound().build();
    }

    // Ma'lumotlar bazasidagi foreign key/unique cheklovi buzilishi (masalan
    // bog'liq yozuvlari bor ota-obyektni o'chirishga urinish) — xom SQL
    // xato matnini ("could not execute statement [Cannot delete or update
    // a parent row...]") to'g'ridan-to'g'ri foydalanuvchiga ko'rsatish
    // o'rniga, tushunarli xabar bilan 409 CONFLICT qaytariladi.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Ma'lumotlar bazasi cheklovi buzildi: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Bu amalni bajarib bo'lmadi — bog'liq ma'lumotlar mavjud."));
    }

    // Qolgan barcha xatolar (biznes-validatsiya RuntimeException'lari va h.k.)
    // Loyihaning ko'p joyida (masalan users.js, teacher.js) frontend shu
    // {"error": "..."} formatini va aynan shu xabar matnini kutadi, shuning
    // uchun mavjud xulq-atvor saqlab qolindi — faqat endi har bir xato
    // log'ga ham yoziladi, shunda kutilmagan (masalan NPE) xatolarni
    // production'da ko'rish mumkin bo'ladi.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAnyException(Exception ex) {
        log.error("So'rovni bajarishda xatolik: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
