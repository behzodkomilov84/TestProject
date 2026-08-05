package behzoddev.testproject.dto.subscription;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

// OWNER uchun to'lov tarixi/hisobot sahifasidagi umumiy ko'rsatkichlar.
@Builder
public record SubscriptionStatsDto(
        BigDecimal totalRevenue,        // Barcha vaqt bo'yicha CONFIRMED to'lovlar yig'indisi
        BigDecimal thisMonthRevenue,    // Joriy oydagi CONFIRMED to'lovlar yig'indisi
        long totalConfirmedCount,       // Jami tasdiqlangan to'lovlar soni
        long activeSubscribersCount,    // Hozir faol (muddati o'tmagan) obunachilar soni
        long pendingCount,              // Tasdiq kutayotgan so'rovlar soni
        List<MonthlyRevenueDto> monthlyBreakdown // Oxirgi 12 oy bo'yicha tushum
) {
}
