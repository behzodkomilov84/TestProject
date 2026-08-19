package behzoddev.testproject.dto.payment;

import lombok.Builder;

// Frontend "onlayn to'lov" blokini ko'rsatish-ko'rsatmaslikni va narxni
// shundan bilib oladi (shlyuz sozlanmagan bo'lsa — tugmalar yashirin).
@Builder
public record PaymentConfigDto(
        boolean clickEnabled,
        long pricePerMonthSom
) {
}
