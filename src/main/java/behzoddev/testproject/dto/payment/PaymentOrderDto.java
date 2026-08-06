package behzoddev.testproject.dto.payment;

import lombok.Builder;

import java.math.BigDecimal;

// Order yaratilgach frontend'ga qaytariladi — checkoutUrl'ga o'tkazish uchun.
@Builder
public record PaymentOrderDto(
        Long id,
        BigDecimal amount,
        int durationMonths,
        String status,
        String checkoutUrl
) {
}
