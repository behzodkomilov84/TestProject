package behzoddev.testproject.service.payment;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.PaymentSettingsRepository;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.PaymentSettings;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.service.CourseSubscriptionService;
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
    private final CourseRepository courseRepository;
    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseSubscriptionService courseSubscriptionService;

    private static final long DEFAULT_MIN_AMOUNT_SOM = 1000;
    private static final int DEFAULT_COURSE_DURATION_MONTHS = 1;

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

    // Foydalanuvchi kurs sahifasidagi "💳 Click orqali to'lash" tugmasi
    // bilan boshlaydi — OWNER tasdig'ini kutmasdan (requestSubscription/
    // subscribe() dagi qo'lda oqimdan farqli), to'lov muvaffaqiyatli
    // bo'lishi bilanoq (markPaid) kursga kirish avtomatik ochiladi.
    @Transactional
    public PaymentOrder createCourseOrder(User user, Long courseId, Integer durationMonths) {
        int months = durationMonths == null || durationMonths <= 0
                ? DEFAULT_COURSE_DURATION_MONTHS
                : durationMonths;

        if (months > 24) {
            throw new IllegalArgumentException("❌Muddat 24 oydan ko'p bo'lishi mumkin emas");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));

        if (course.isFree()) {
            throw new IllegalArgumentException("❌Bu kurs bepul — to'lov shart emas");
        }

        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("❌Kurs narxi hali belgilanmagan — OWNER bilan bog'laning");
        }

        if (courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                user.getId(), courseId, CourseSubscriptionStatus.CONFIRMED, LocalDateTime.now())) {
            throw new IllegalArgumentException("❌Siz allaqachon shu kursga obuna bo'lgansiz");
        }

        BigDecimal amount = course.getPrice().multiply(BigDecimal.valueOf(months));

        BigDecimal minAmount = getMinAmountSom();
        if (amount.compareTo(minAmount) < 0) {
            throw new IllegalArgumentException(
                    "❌To'lov summasi minimal chegaradan (" + minAmount.toPlainString() + " so'm) kam bo'lishi mumkin emas");
        }

        PaymentOrder order = PaymentOrder.builder()
                .user(user)
                .amount(amount)
                .durationMonths(months)
                .status(PaymentOrderStatus.CREATED)
                .courseId(courseId)
                .build();

        paymentOrderRepository.save(order);
        log.info("Kurs uchun to'lov buyurtmasi yaratildi: id={}, user={}, course={}, summa={}, muddat={} oy",
                order.getId(), user.getUsername(), course.getTitle(), amount, months);

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
    // courseId belgilanganmi (kurs to'lovi) yoki yo'qmi (ADMIN-rol
    // obunasi, avvalgi xulq-atvor) — shunga qarab tegishli servisga boradi.
    @Transactional
    public void markPaid(PaymentOrder order) {
        if (order.getStatus() == PaymentOrderStatus.PAID) return;

        if (order.getStatus() == PaymentOrderStatus.CANCELLED) {
            throw new IllegalStateException("Bu buyurtma allaqachon bekor qilingan");
        }

        Long confirmedSubscriptionId;
        if (order.getCourseId() != null) {
            confirmedSubscriptionId = courseSubscriptionService.confirmOnline(
                    order.getUser(), order.getCourseId(), order.getAmount(), order.getDurationMonths()).id();
        } else {
            SubscriptionDto subscription = subscriptionService.confirmOnline(
                    order.getUser(), order.getAmount(), order.getDurationMonths());
            confirmedSubscriptionId = subscription.id();
        }

        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setSubscriptionId(confirmedSubscriptionId);
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
        if (order.getCourseId() != null) {
            courseSubscriptionService.reverseOnline(order.getSubscriptionId());
        } else {
            subscriptionService.reverseOnline(order.getUser(), order.getSubscriptionId());
        }
    }
}
