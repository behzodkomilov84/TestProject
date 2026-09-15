package behzoddev.testproject.controller.api;

import behzoddev.testproject.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// "🔄 Serverni qayta ishga tushirish" — Owner Panel'dan bitta tugma bilan
// (foydalanuvchi so'rovi, 2026-09-15: "bitta knopka bilan deploy(yoki
// restart server) qilish imkoni bormi" — ikkita variant taklif qilingan
// (A: shunchaki restart, B: docker socket/agent orqali to'liq qayta
// build+deploy); foydalanuvchi FAQAT A'ni tanladi).
//
// Bu YANGI KOD OLIB KELMAYDI (jar/image o'zgarmaydi) — faqat joriy JVM
// jarayonini to'xtatadi. docker-compose.prod.yml'dagi "app" xizmati uchun
// "restart: unless-stopped" siyosati borligi sabab, konteyner Docker
// tomonidan AVTOMATIK, xuddi shu image bilan qayta ko'tariladi (odatda
// ~30-60 soniya). Docker socket ulash yoki SSH kerak emas — ilova faqat
// o'zini o'zi to'xtatadi, shu sabab bu B variantidagi ("ilova ichidan
// butun hostni boshqarish huquqi") xavfsizlik xatarisiz.
@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasAuthority('ROLE_OWNER')")
@Slf4j
public class SystemController {

    @PostMapping("/restart")
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
}
