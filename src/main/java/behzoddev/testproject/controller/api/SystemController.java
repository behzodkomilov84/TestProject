package behzoddev.testproject.controller.api;

import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ClamAvScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

// "🔄 Serverni qayta ishga tushirish" — Owner Panel'dan bitta tugma bilan
// (foydalanuvchi so'rovi, 2026-09-15: "bitta knopka bilan deploy(yoki
// restart server) qilish imkoni bormi" — ikkita variant taklif qilingan
// (A: shunchaki restart, B: docker socket/agent orqali to'liq qayta
// build+deploy); foydalanuvchi FAQAT A'ni tanladi).
//
// Restart YANGI KOD OLIB KELMAYDI (jar/image o'zgarmaydi) — faqat joriy
// JVM jarayonini to'xtatadi. docker-compose.prod.yml'dagi "app" xizmati
// uchun "restart: unless-stopped" siyosati borligi sabab, konteyner
// Docker tomonidan AVTOMATIK, xuddi shu image bilan qayta ko'tariladi
// (odatda ~30-60 soniya). Docker socket ulash yoki SSH kerak emas —
// ilova faqat o'zini o'zi to'xtatadi.
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
public class SystemController {

    private final DataSource dataSource;
    private final ClamAvScanService clamAvScanService;

    @PostMapping("/restart")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public Map<String, Object> restart(@AuthenticationPrincipal User currentUser) {
        log.warn("⚠️ Server qayta ishga tushirilmoqda — buyruqni bergan foydalanuvchi: {}",
                currentUser != null ? currentUser.getUsername() : "noma'lum");

        // HTTP javobi brauzerga YETIB BORGUNCHA bir oz kutamiz — System.exit()
        // darhol chaqirilsa, javob hali to'liq yozilib ulgurmasligi mumkin edi.
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "manual-restart-trigger");
        shutdownThread.setDaemon(false);
        shutdownThread.start();

        return Map.of(
                "status", "restarting",
                "message", "Server qayta ishga tushirilmoqda. Taxminan 30–60 soniyadan so'ng qaytadan urinib ko'ring."
        );
    }

    // Restart tugmasidan keyin "server qaytdi, DB/ClamAV yaxshi ishlayaptimi"
    // degan hisobotni ko'rsatish uchun (foydalanuvchi so'rovi, 2026-09-15:
    // "server muvaffaqiyatli qayta yuklanganligi, DB yaxshi ishlayapti,
    // ClamAV to'g'ri ishlayapti... kabi habarlarni ham ko'ra olishim
    // kerak"). QASDDAN autentifikatsiyasiz (SecurityConfig'da permitAll) —
    // restart sessiyalarni tozalaydi, shu payt hali login qilinmagan
    // holatda ham chaqirilishi kerak. Faqat oddiy UP/DOWN holatini
    // qaytaradi — hech qanday maxfiy/ichki ma'lumot oshkor qilinmaydi.
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", "UP");

        boolean dbUp;
        try (Connection connection = dataSource.getConnection()) {
            dbUp = connection.isValid(2);
        } catch (Exception e) {
            dbUp = false;
        }
        result.put("database", dbUp ? "UP" : "DOWN");

        Boolean clamAvUp = clamAvScanService.ping();
        result.put("clamav", clamAvUp == null ? "DISABLED" : (clamAvUp ? "UP" : "DOWN"));

        return result;
    }
}
