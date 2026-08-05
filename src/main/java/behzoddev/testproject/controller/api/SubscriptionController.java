package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.subscription.ConfirmSubscriptionDto;
import behzoddev.testproject.dto.subscription.CreateSubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // Telegram orqali kelgan (yoki boshqa manbadan PENDING holatidagi)
    // so'rovni tasdiqlash — ADMIN roli shu muddatga beriladi.
    @PostMapping("/{id}/confirm")
    public ResponseEntity<SubscriptionDto> confirm(
            @PathVariable Long id,
            @RequestBody(required = false) ConfirmSubscriptionDto dto,
            @AuthenticationPrincipal User owner
    ) {
        Integer months = dto == null ? null : dto.durationMonths();
        return ResponseEntity.ok(subscriptionService.confirm(id, months, owner));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.cancel(id));
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionDto>> list(
            @RequestParam(required = false) String status
    ) {
        if ("PENDING".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(subscriptionService.listPending());
        }
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
}
