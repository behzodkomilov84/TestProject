package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.CourseSubscriptionDto;
import behzoddev.testproject.dto.course.CreateCourseSubscriptionDto;
import behzoddev.testproject.dto.payment.CreatePaymentOrderDto;
import behzoddev.testproject.dto.payment.PaymentOrderDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseSubscriptionService;
import behzoddev.testproject.service.payment.ClickService;
import behzoddev.testproject.service.payment.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Kurs obunalari — qo'lda tasdiqlaydi (Telegram oqimi yo'q). ROLE_OWNER
// cheklovsiz barcha kurslarni boshqaradi; ROLE_ADMIN (kurs muallifi) ham
// shu yerga kira oladi, lekin faqat O'ZI yaratgan kurs bilan bog'liq
// amallarni bajara oladi — haqiqiy tekshiruv CourseSubscriptionService
// ichida (checkCanManage) amalga oshiriladi (foydalanuvchi so'rovi,
// 2026-09-07: "билдиришномалар фақат шу админнинг ўзига келсин. OWNER
// учун чеклов йўқ").
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
public class CourseSubscriptionController {

    private final CourseSubscriptionService courseSubscriptionService;
    private final PaymentOrderService paymentOrderService;
    private final ClickService clickService;

    // Istalgan login qilgan foydalanuvchi (rolidan qat'i nazar) kursga
    // obuna bo'lishni so'rashi mumkin — method-level @PreAuthorize
    // class-level "faqat OWNER" cheklovini shu endpoint uchun bekor qiladi.
    @PostMapping("/api/courses/{courseId}/subscriptions/request")
    @PreAuthorize("isAuthenticated()")
    public void request(@PathVariable Long courseId, @AuthenticationPrincipal User user) {
        courseSubscriptionService.requestSubscription(courseId, user);
    }

    // "🎁 3 kunlik bepul sinov" — OWNER tasdig'ini kutmasdan, DARHOL
    // kirish beriladi (foydalanuvchi so'rovi, 2026-09-09).
    @PostMapping("/api/courses/{courseId}/subscriptions/trial")
    @PreAuthorize("isAuthenticated()")
    public CourseSubscriptionDto startTrial(@PathVariable Long courseId, @AuthenticationPrincipal User user) {
        return courseSubscriptionService.startFreeTrial(courseId, user);
    }

    // "💳 Click orqali to'lash" — OWNER tasdig'ini kutmasdan, to'lov
    // muvaffaqiyatli bo'lishi bilanoq kursga kirish avtomatik ochiladi
    // (PaymentOrderService.markPaid -> CourseSubscriptionService.confirmOnline).
    @PostMapping("/api/courses/{courseId}/subscriptions/pay")
    @PreAuthorize("isAuthenticated()")
    public PaymentOrderDto pay(
            @PathVariable Long courseId,
            @RequestBody CreatePaymentOrderDto dto,
            @AuthenticationPrincipal User user
    ) {
        PaymentOrder order = paymentOrderService.createCourseOrder(user, courseId, dto.durationMonths());

        String provider = dto.provider() == null ? "" : dto.provider().toUpperCase();
        String checkoutUrl;

        if ("CLICK".equals(provider)) {
            if (!clickService.isEnabled()) {
                throw new IllegalStateException("❌Click hozircha ulanmagan");
            }
            checkoutUrl = clickService.buildPayUrl(order, "/courses/" + courseId);
        } else {
            throw new IllegalArgumentException("❌To'lov tizimini tanlang (Click)");
        }

        return PaymentOrderDto.builder()
                .id(order.getId())
                .amount(order.getAmount())
                .durationMonths(order.getDurationMonths())
                .status(order.getStatus().name())
                .checkoutUrl(checkoutUrl)
                .build();
    }

    @PostMapping("/api/courses/{courseId}/subscriptions")
    public CourseSubscriptionDto subscribe(
            @PathVariable Long courseId,
            @RequestBody CreateCourseSubscriptionDto dto,
            @AuthenticationPrincipal User owner
    ) {
        return courseSubscriptionService.subscribe(courseId, dto, owner);
    }

    @GetMapping("/api/courses/{courseId}/subscriptions")
    public List<CourseSubscriptionDto> listForCourse(@PathVariable Long courseId,
                                                       @AuthenticationPrincipal User requester) {
        return courseSubscriptionService.listForCourse(courseId, requester);
    }

    @GetMapping("/api/course-subscriptions")
    public List<CourseSubscriptionDto> listAll(@AuthenticationPrincipal User requester) {
        return courseSubscriptionService.listAll(requester);
    }

    @PostMapping("/api/course-subscriptions/{id}/cancel")
    public void cancel(@PathVariable Long id, @AuthenticationPrincipal User requester) {
        courseSubscriptionService.cancel(id, requester);
    }
}
