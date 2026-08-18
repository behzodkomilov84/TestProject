package behzoddev.testproject.controller.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// VAQTINCHALIK — Sentry integratsiyasini bir martalik tekshirish uchun
// (logback-spring.xml'dagi SentryAppender haqiqatan ham ERROR loglarni
// Sentry'ga yetkazayotganini tasdiqlash). Tekshiruvdan so'ng shu fayl va
// SecurityConfig'dagi mos qator OLIB TASHLANADI — production'da doimiy
// qolib, tashqaridan chaqirib Sentry kvotasini suiiste'mol qilishning
// oldini olish uchun.
@Slf4j
@RestController
public class SentryTestController {

    @GetMapping("/api/sentry-test")
    public Map<String, String> triggerTestError() {
        log.error("Sentry integratsiyasini tekshirish uchun sinov xatosi", new RuntimeException("Sentry test event"));
        return Map.of("status", "logged");
    }
}
