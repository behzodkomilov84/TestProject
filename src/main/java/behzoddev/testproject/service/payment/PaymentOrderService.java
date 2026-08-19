package behzoddev.testproject.service.payment;

import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.PaymentSettingsRepository;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.PaymentSettings;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

// PaymentOrder — foydalanuvchi "ADMIN huquqini onlayn sotib olish"ni
// boshlaganda yaratiladigan buyurtma (Click bilan gaplashishdan oldin).
// Checkout URL ClickService'da tuziladi, bu servis esa faqat order'ning
// umumiy hayot aylanishini boshqaradi.
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    // Bir oylik ADMIN obunasi narxi (so'm). Hozircha yagona, hammaga bir xil
    // narx — guruh/tarif rejalar ROADMAP'da alohida band sifatida qoldirilgan.
    @Value("${app.payments.price-per-month-som:50000}")
    private long pricePerMonthSom;

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentSettingsRepository paymentSettingsRepository;
    private final SubscriptionService subscriptionService;

    private static final long DEFAULT_MIN_AMOUNT_SOM = 1000;

    @Transactional
    public PaymentOrder createOrder(User user, int durationMonths) {
        if (durationMonths < 1 || durationMonths > 24) {
            throw new IllegalArgumentException("❌Muddat 1 dan 24 oygacha bo'lishi kerak");
        }

        if (user.hasRole("ROLE_OWNER")) {
            throw new IllegalArgumentException("❌OWNER uchun ADMIN obunasi kerak emas");
        }

        BigDecimal amount = BigDecimal.valueOf(pricePerMonthSom).multiply(BigDecimal.valueOf(durationMonths));

        // Click kabi shlyuzlar juda kichik summalarni rad etadi —
        // OWNER /users sahifasidan sozlagan chegaradan tekshiramiz.
        BigDecimal minAmount = getMinAmountSom();
        if (amount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException(
                    "❌To'lov summasi minimal chegaradan (" + minAmount.toPlainString() + " so'm) kam bo'lishi mumkin emas");
        }

        PaymentOrder order = PaymentOrder.builder()
                .user(user)
                .amount(amount)
                .durationMonths(durationMonths)
                .status(PaymentOrderStatus.CREATED)
                .build();

        paymentOrderRepository.save(order);
        log.info("To'lov buyurtmasi yaratildi: id={}, user={}, summa={}, muddat={} oy",
                order.getId(), user.getUsername(), amount, durationMonths);

        return order;
    }

    @Transactional(readOnly = true)
    public PaymentOrder getOrderOrThrow(Long orderId) {
        return paymentOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("To'lov buyurtmasi topilmadi"));
    }

    public long getPricePerMonthSom() {
        return pricePerMonthSom;
    }

    // Click'ning real minimal tranzaksiya chegarasi — OWNER buni
    // /users sahifasidan (redeploy'siz) o'zgartira oladi. Qator hali
    // yaratilmagan bo'lsa (masalan yangi environment), standart qiymat
    // bilan avtomatik yaratiladi.
    @Transactional
    public BigDecimal getMinAmountSom() {
        return getOrCreateSettings().getMinAmountSom();
    }

    @Transactional
    public BigDecimal updateMinAmountSom(BigDecimal newMinAmount) {
        if (newMinAmount == null || newMinAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("❌Minimal summa musbat son bo'lishi kerak");
        }

        PaymentSettings settings = getOrCreateSettings();
        settings.setMinAmountSom(newMinAmount);
        paymentSettingsRepository.save(settings);

        log.info("To'lov minimal summasi o'zgartirildi: {}", newMinAmount);
        return newMinAmount;
    }

    private PaymentSettings getOrCreateSettings() {
        return paymentSettingsRepository.findById(1L)
                .orElseGet(() -> {
                    PaymentSettings settings = new PaymentSettings();
                    settings.setId(1L);
                    settings.setMinAmountSom(BigDecimal.valueOf(DEFAULT_MIN_AMOUNT_SOM));
                    return paymentSettingsRepository.save(settings);
                });
    }

    // Click Complete muvaffaqiyatli bo'lganda
    // chaqiriladi. Idempotent — order allaqachon PAID bo'lsa jim qaytadi
    // (provayderlar bir xil so'rovni bir necha marta qayta yuborishi mumkin).
    @Transactional
    public void markPaid(PaymentOrder order) {
        if (order.getStatus() == PaymentOrderStatus.PAID) return;

        if (order.getStatus() == PaymentOrderStatus.CANCELLED) {
            throw new IllegalStateException("Bu buyurtma allaqachon bekor qilingan");
        }

        SubscriptionDto subscription = subscriptionService.confirmOnline(
                order.getUser(), order.getAmount(), order.getDurationMonths());

        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setSubscriptionId(subscription.id());
        paymentOrderRepository.save(order);
    }

    // Perform'dan OLDIN (hali to'lanmagan) bekor qilinsa — shunchaki order
    // holatini yopamiz, Subscription hali yaratilmagani uchun boshqa hech narsa qilish shart emas.
    @Transactional
    public void markCancelled(PaymentOrder order) {
        if (order.getStatus() == PaymentOrderStatus.PAID) return; // to'langanini shu yerdan bekor qilib bo'lmaydi
        order.setStatus(PaymentOrderStatus.CANCELLED);
        paymentOrderRepository.save(order);
    }

    // Perform'dan KEYIN (to'langan bo'lsa ham) chargeback/qaytarish sodir
    // bo'lsa — order PAID bo'lib qoladi (tarix uchun), lekin berilgan
    // ADMIN huquqi SubscriptionService.reverseOnline orqali bekor qilinadi.
    @Transactional
    public void reversePaidOrder(PaymentOrder order) {
        subscriptionService.reverseOnline(order.getUser(), order.getSubscriptionId());
    }
}
