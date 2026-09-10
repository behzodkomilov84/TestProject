package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.subscription.CreateSubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import behzoddev.testproject.dto.subscription.UpdateSubscriptionDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Faqat OWNER foydalana oladi — cheklov SecurityConfig'da
// "/api/subscriptions/**" uchun ROLE_OWNER sifatida qo'yilgan.
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // OWNER to'lovni qo'lda qayd qilib, darhol tasdiqlaydi (naqd/karta orqali
    // saytdan tashqarida qabul qilingan to'lovlar uchun).
    @PostMapping
    public ResponseEntity<SubscriptionDto> create(
            @RequestBody CreateSubscriptionDto dto,
            @AuthenticationPrincipal User owner
    ) {
        return ResponseEntity.ok(subscriptionService.createManual(dto, owner));
    }

    // "/{id}/confirm" (PENDING so'rovni tasdiqlash) OLIB TASHLANDI
    // (foydalanuvchi so'rovi, 2026-09-10: "bu logikani barcha joydan
    // olib tashla" — qo'lda/botdagi PENDING so'rov yaratish yo'li
    // umuman yo'qotildi, shuning uchun tasdiqlaydigan hech narsa
    // qolmadi).

    // PENDING so'rovni rad etadi YOKI allaqachon FAOL (CONFIRMED) obunani
    // bekor qilib, ROLE_ADMIN'ni darhol olib tashlaydi (foydalanuvchi
    // so'rovi, 2026-09-09: "қўлда берилган админни бекор қилишни қаерга
    // қиламан?").
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionDto> cancel(@PathVariable Long id, @AuthenticationPrincipal User requester) {
        return ResponseEntity.ok(subscriptionService.cancel(id, requester));
    }

    // "✏️ Tahrirlash" — mavjud obunaning summasi/muddatini o'zgartiradi
    // (foydalanuvchi so'rovi, 2026-09-09).
    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionDto> update(
            @PathVariable Long id,
            @RequestBody UpdateSubscriptionDto dto,
            @AuthenticationPrincipal User requester
    ) {
        return ResponseEntity.ok(subscriptionService.updateSubscription(
                id, dto.amount(), dto.durationMonths(), dto.source(), requester));
    }

    // "🗑️ O'chirish" — "cancel"dan farqli, yozuvni BUTUNLAY o'chiradi.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User requester) {
        subscriptionService.delete(id, requester);
        return ResponseEntity.ok().build();
    }

    // "status=PENDING" filtri OLIB TASHLANDI — SubscriptionService.listPending()
    // endi mavjud emas (foydalanuvchi so'rovi, 2026-09-10). "Barcha
    // obunalar" jadvali (listAll) baribir HAMMA holatni o'z ichiga oladi.
    @GetMapping
    public ResponseEntity<List<SubscriptionDto>> list() {
        return ResponseEntity.ok(subscriptionService.listAll());
    }

    // To'lov tarixi/hisobot sahifasi uchun umumiy ko'rsatkichlar (jami
    // tushum, shu oy, faol obunachilar va h.k.).
    @GetMapping("/stats")
    public ResponseEntity<SubscriptionStatsDto> stats() {
        return ResponseEntity.ok(subscriptionService.getStats());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SubscriptionDto>> listForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.listForUser(userId));
    }

    // Joriy hisobotni OWNER'ning o'z emailiga yuboradi.
    @PostMapping("/stats/email")
    public ResponseEntity<Map<String, String>> emailStats(@AuthenticationPrincipal User owner) {
        subscriptionService.emailStatsReport(owner);
        return ResponseEntity.ok(Map.of("message", "✅ Hisobot emailingizga yuborildi: " + owner.getEmail()));
    }
}
