package behzoddev.testproject.controller.api.payment;

import behzoddev.testproject.dto.payment.CreatePaymentOrderDto;
import behzoddev.testproject.dto.payment.PaymentConfigDto;
import behzoddev.testproject.dto.payment.PaymentOrderDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.payment.ClickService;
import behzoddev.testproject.service.payment.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

// Foydalanuvchi (USER) ROLE_ADMIN obunasini o'zi onlayn sotib olishni shu
// yerdan boshlaydi — /profile sahifasidagi "Onlayn to'lov" blokidan.
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentOrderController {

    private final PaymentOrderService paymentOrderService;
    private final ClickService clickService;

    // Frontend shlyuz sozlanganmi/narxi qancha ekanini shundan bilib, "Onlayn
    // to'lov" blokini shunga qarab ko'rsatadi/yashiradi.
    @GetMapping("/config")
    public PaymentConfigDto config() {
        return PaymentConfigDto.builder()
                .clickEnabled(clickService.isEnabled())
                .pricePerMonthSom(paymentOrderService.getPricePerMonthSom())
                .build();
    }

    @PostMapping("/orders")
    public PaymentOrderDto createOrder(@RequestBody CreatePaymentOrderDto dto, @AuthenticationPrincipal User user) {
        PaymentOrder order = paymentOrderService.createOrder(user, dto.durationMonths() == null ? 1 : dto.durationMonths());

        String provider = dto.provider() == null ? "" : dto.provider().toUpperCase();
        String returnUrl = "/profile";
        String checkoutUrl;

        if ("CLICK".equals(provider)) {
            if (!clickService.isEnabled()) {
                throw new IllegalStateException("❌Click hozircha ulanmagan");
            }
            checkoutUrl = clickService.buildPayUrl(order, returnUrl);
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

    // Click'ning minimal tranzaksiya summasi — /users sahifasida
    // OWNER ko'rib/o'zgartirib turishi uchun.
    @GetMapping("/min-amount")
    public Map<String, BigDecimal> getMinAmount() {
        return Map.of("minAmountSom", paymentOrderService.getMinAmountSom());
    }

    @PutMapping("/min-amount")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public Map<String, BigDecimal> updateMinAmount(@RequestBody Map<String, BigDecimal> body) {
        BigDecimal updated = paymentOrderService.updateMinAmountSom(body.get("minAmountSom"));
        return Map.of("minAmountSom", updated);
    }
}
