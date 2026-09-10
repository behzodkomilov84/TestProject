package behzoddev.testproject.service;

import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.SubscriptionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.subscription.CreateSubscriptionDto;
import behzoddev.testproject.dto.subscription.MonthlyRevenueDto;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.Subscription;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.RoleAuditAction;
import behzoddev.testproject.entity.enums.RoleAuditSource;
import behzoddev.testproject.entity.enums.SubscriptionSource;
import behzoddev.testproject.entity.enums.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.NoSuchElementException;

/**
 * ROLE_ADMIN'ni to'lov (obuna) asosida, muddatli tarzda berish/olib
 * tashlash logikasi.
 * <p>
 * Uchta manba orqali obuna yaratilishi mumkin:
 * - MANUAL: OWNER o'zi /users sahifasida to'lovni qo'lda qayd qiladi va
 *   darhol tasdiqlaydi (naqd/karta orqali saytdan tashqarida to'langan).
 * - TELEGRAM: foydalanuvchi botga to'lov haqida xabar yuboradi, PENDING
 *   holatida yaratiladi, OWNER keyin /users sahifasida tasdiqlaydi.
 * - ONLINE: Click shlyuzi orqali avtomatik tasdiqlangan to'lovlar uchun
 *   (foydalanuvchi o'zi, OWNER ishtirokisiz).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final int DEFAULT_DURATION_MONTHS = 1;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final RoleAuditService roleAuditService;
    private final EmailService emailService;
    private final PaymentOrderRepository paymentOrderRepository;

    @Transactional
    public SubscriptionDto createManual(CreateSubscriptionDto dto, User owner) {
        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));

        if (dto.amount() == null || dto.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("To'lov summasi noto'g'ri");
        }

        int months = dto.durationMonths() == null ? DEFAULT_DURATION_MONTHS : dto.durationMonths();
        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = Subscription.builder()
                .user(user)
                .amount(dto.amount())
                .source(SubscriptionSource.MANUAL)
                .status(SubscriptionStatus.CONFIRMED)
                .startDate(now)
                .endDate(now.plusMonths(months))
                .confirmedBy(owner)
                .note(dto.note())
                .build();

        subscriptionRepository.save(subscription);
        grantAdmin(user, owner);

        notificationService.create(user,
                "✅ ADMIN huquqingiz tasdiqlandi! Endi " + months + " oy davomida o'qituvchi sifatida ishlashingiz mumkin.",
                "/profile");

        log.info("Obuna (MANUAL) tasdiqlandi: user={}, muddat={} oy, owner={}",
                user.getUsername(), months, owner.getUsername());

        return toDto(subscription);
    }

    // "createPendingFromTelegram" (Telegram bot "/pay <summa>" qo'lda
    // to'lov so'rovi) va "confirm" (o'sha PENDING so'rovni OWNER
    // tasdiqlashi) OLIB TASHLANDI (foydalanuvchi so'rovi, 2026-09-10:
    // "bu logikani barcha joydan olib tashla, botdan ham. To'lovlarni
    // faqat hozircha clickdan qabul qilamiz"). ADMIN huquqini olish
    // endi FAQAT ikki yo'l bilan: Click orqali onlayn to'lov
    // (confirmOnline — avtomatik CONFIRMED) yoki OWNER'ning qo'lda
    // qayd qilishi (createManual — darhol CONFIRMED). Eski PENDING
    // qatorlar (agar tarixda qolgan bo'lsa) hali ham "Barcha
    // obunalar"da ko'rinadi va cancel()/delete() orqali boshqarilishi
    // mumkin — faqat ularni ENDI hech kim yaratolmaydi.

    // Click orqali avtomatik to'lov muvaffaqiyatli yakunlanganda
    // (PaymentOrderService.markPaid) chaqiriladi — inson (OWNER) ishtirok
    // etmagani uchun confirmedBy=null, source=ONLINE.
    @Transactional
    public SubscriptionDto confirmOnline(User user, BigDecimal amount, int months) {
        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = Subscription.builder()
                .user(user)
                .amount(amount)
                .source(SubscriptionSource.ONLINE)
                .status(SubscriptionStatus.CONFIRMED)
                .startDate(now)
                .endDate(now.plusMonths(months))
                .note("Onlayn to'lov (Click) orqali avtomatik tasdiqlandi")
                .build();

        subscriptionRepository.save(subscription);
        grantAdmin(user, null);

        notificationService.create(user,
                "✅ ADMIN huquqingiz onlayn to'lov orqali tasdiqlandi! Endi " + months +
                        " oy davomida o'qituvchi sifatida ishlashingiz mumkin.",
                "/profile");

        log.info("Obuna (ONLINE) tasdiqlandi: user={}, muddat={} oy", user.getUsername(), months);

        return toDto(subscription);
    }

    // To'lov muvaffaqiyatli yakunlangandan KEYIN shlyuz chargeback/qaytarish
    // haqida xabar bersa — allaqachon berilgan ADMIN huquqini bekor qilamiz.
    // Hozircha ClickService bu holatni yubormaydi (Click webhook'i shunday
    // xabar bermaydi), lekin PaymentOrderService.reversePaidOrder shu yerga
    // ulanadi — kelajakda kerak bo'lsa tayyor.
    // MUHIM: subscriptionId — aynan SHU to'lov orqali yaratilgan obuna ID'si
    // (PaymentOrder.subscriptionId). Avval bu parametr yo'q edi va "boshqa
    // faol obuna bormi" tekshiruvi doim shu obunaning o'zini topib, ADMIN
    // hech qachon haqiqatda bekor qilinmas edi — shuning uchun bu yerda
    // avval o'sha aniq obunani CANCELLED qilamiz, keyin qolganini tekshiramiz.
    @Transactional
    public void reverseOnline(User user, Long subscriptionId) {
        if (subscriptionId != null) {
            cancelConfirmedById(subscriptionId);
        }

        boolean hasOtherActive = subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                user.getId(), SubscriptionStatus.CONFIRMED, LocalDateTime.now());

        if (!hasOtherActive) {
            revokeAdmin(user);
            notificationService.create(user,
                    "⚠️ Onlayn to'lovingiz bekor qilindi (qaytarildi), shunga ko'ra ADMIN huquqi ham bekor qilindi.",
                    "/profile");
            log.info("Onlayn to'lov bekor qilindi, ROLE_ADMIN olib tashlandi: {}", user.getUsername());
        }
    }

    // reverseOnline uchun ichki yordamchi — mavjud cancel(id) PENDING'ni talab
    // qiladi, bu esa CONFIRMED (allaqachon to'langan) obunani bekor qilishi kerak.
    private void cancelConfirmedById(Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId).ifPresent(s -> {
            if (s.getStatus() == SubscriptionStatus.CONFIRMED) {
                s.setStatus(SubscriptionStatus.CANCELLED);
                subscriptionRepository.save(s);
            }
        });
    }

    // "❌ Bekor qilish" — PENDING so'rovni rad etadi VA (foydalanuvchi
    // so'rovi, 2026-09-09: "қўлда берилган админни бекор қилишни қаерга
    // қиламан?") ALLAQACHON FAOL (CONFIRMED — qo'lda yoki onlayn berilgan)
    // obunani ham bekor qilib, agar boshqa faol obuna qolmagan bo'lsa,
    // ROLE_ADMIN'ni darhol olib tashlaydi. Ilgari bu metod FAQAT PENDING
    // uchun ishlardi — CONFIRMED obunani bekor qilishning hech qanday
    // UI/endpoint yo'li yo'q edi (faqat scheduled job orqali muddat
    // tugashi yoki Click chargeback orqali avtomatik bekor bo'lardi).
    @Transactional
    public SubscriptionDto cancel(Long subscriptionId, User requester) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("So'rov topilmadi"));

        SubscriptionStatus previousStatus = subscription.getStatus();
        if (previousStatus != SubscriptionStatus.PENDING && previousStatus != SubscriptionStatus.CONFIRMED) {
            throw new IllegalArgumentException("Faqat kutilayotgan yoki faol obunani bekor qilish mumkin");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);

        String message;
        if (previousStatus == SubscriptionStatus.PENDING) {
            message = "❌ ADMIN huquqiga so'rovingiz administrator tomonidan rad etildi.";
        } else {
            User user = subscription.getUser();
            // Shu obuna ENDI CANCELLED bo'lgani uchun, quyidagi tekshiruv
            // uni o'z ichiga olmaydi — faqat BOSHQA hali faol obunalar bor-
            // yo'qligini ko'radi (reverseOnline/expireSubscriptions bilan
            // bir xil mantiq).
            boolean hasOtherActive = subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                    user.getId(), SubscriptionStatus.CONFIRMED, LocalDateTime.now());
            if (!hasOtherActive) {
                revokeAdmin(user, requester, RoleAuditSource.MANUAL);
            }
            message = "⚠️ ADMIN huquqingiz administrator tomonidan bekor qilindi.";
        }

        // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09:
        // "Foydalanuvchini obunasi rad etildi, lekin bildirishnomaga
        // kelmadi USER ga") — CourseSubscriptionService.cancel() bilan bir
        // xil kamchilik, shu yerda ham tuzatildi.
        notificationService.create(subscription.getUser(), message, "/profile");

        log.info("ADMIN obunasi {}: user={}, requester={}",
                previousStatus == SubscriptionStatus.PENDING ? "rad etildi" : "bekor qilindi",
                subscription.getUser().getUsername(), requester.getUsername());

        return toDto(subscription);
    }

    // "✏️ Tahrirlash" — mavjud obunaning summasi/muddatini o'zgartiradi
    // (foydalanuvchi so'rovi, 2026-09-09: "/admin-subscriptions'ga
    // tahrirlash, o'chirishni ham qo'sh"). CANCELLED/EXPIRED obunani tahrirlash
    // uni QAYTA FAOLlashtiradi VA ROLE_ADMIN'ni qayta beradi — alohida
    // "qayta tiklash" tugmasi shart emas (CourseSubscriptionService.
    // updateSubscription bilan bir xil g'oya).
    @Transactional
    public SubscriptionDto updateSubscription(Long subscriptionId, BigDecimal amount, Integer durationMonths,
                                               String source, User requester) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Obuna topilmadi"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("❌To'lov summasi noto'g'ri");
        }
        if (durationMonths == null || durationMonths <= 0) {
            throw new IllegalArgumentException("❌Muddat noto'g'ri");
        }

        // "Manba"ni ham tahrirlash imkoni (foydalanuvchi so'rovi,
        // 2026-09-09: "Админ обуналарини таҳрирлашда манбасини ҳам
        // таҳрирлаш мумкин бўлсин"). Bo'sh/null bo'lsa — mavjud manba
        // o'zgarishsiz qoladi (eski frontend/so'rovlar bilan moslik uchun).
        if (source != null && !source.isBlank()) {
            try {
                subscription.setSource(SubscriptionSource.valueOf(source.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("❌Manba noto'g'ri (MANUAL, ONLINE yoki TELEGRAM bo'lishi kerak)");
            }
        }

        LocalDateTime startDate = subscription.getStartDate() != null ? subscription.getStartDate() : LocalDateTime.now();
        subscription.setAmount(amount);
        subscription.setStartDate(startDate);
        subscription.setEndDate(startDate.plusMonths(durationMonths));
        subscription.setStatus(SubscriptionStatus.CONFIRMED);
        subscription.setConfirmedBy(requester);

        grantAdmin(subscription.getUser(), requester);

        notificationService.create(subscription.getUser(),
                "✏️ ADMIN huquqingiz administrator tomonidan yangilandi (" + durationMonths + " oy).",
                "/profile");

        log.info("ADMIN obunasi tahrirlandi: id={}, user={}, muddat={} oy, requester={}",
                subscriptionId, subscription.getUser().getUsername(), durationMonths, requester.getUsername());

        return toDto(subscription);
    }

    // "🗑️ O'chirish" — "cancel"dan (holatni CANCELLED qilib saqlaydi)
    // farqli, yozuvni BUTUNLAY o'chiradi (foydalanuvchi so'rovi,
    // 2026-09-09). Agar hali FAOL (CONFIRMED) yozuv o'chirilsa — "osilib
    // qolgan" ADMIN huquqi paydo bo'lmasligi uchun (yozuv o'chirilgani
    // sabab hech qanday keyingi job buni topa olmas edi), xuddi cancel()
    // kabi darhol tekshiriladi va, agar boshqa faol obuna qolmagan
    // bo'lsa, ROLE_ADMIN ham olib tashlanadi.
    @Transactional
    public void delete(Long subscriptionId, User requester) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Obuna topilmadi"));

        boolean wasActiveConfirmed = subscription.getStatus() == SubscriptionStatus.CONFIRMED;
        User user = subscription.getUser();

        // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-10: Click
        // panelida to'lov "muvaffaqiyatli" ko'rinsa-da, /payments'da
        // butunlay yo'q edi) — "subscription_id" haqiqiy FK EMAS, shuning
        // uchun bu yozuv o'chirilganda unga ishora qiluvchi PaymentOrder
        // "osilib qolgan" (dangling) havola bilan qolib ketardi.
        paymentOrderRepository.clearSubscriptionId(subscriptionId);

        subscriptionRepository.delete(subscription);

        // "delete" avtomatik flush qilinadi — quyidagi so'rov endi
        // o'chirilgan yozuvni o'zini hisoblamaydi.
        if (wasActiveConfirmed) {
            boolean hasOtherActive = subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                    user.getId(), SubscriptionStatus.CONFIRMED, LocalDateTime.now());
            if (!hasOtherActive) {
                revokeAdmin(user, requester, RoleAuditSource.MANUAL);
                notificationService.create(user,
                        "⚠️ ADMIN huquqingiz administrator tomonidan bekor qilindi.", "/profile");
            }
        }

        log.info("ADMIN obunasi butunlay o'chirildi: id={}, user={}, requester={}",
                subscriptionId, user.getUsername(), requester.getUsername());
    }

    // OWNER uchun to'lov tarixi/hisobot sahifasidagi umumiy ko'rsatkichlar.
    // Faqat CONFIRMED to'lovlar haqiqiy tushum hisoblanadi (PENDING hali
    // to'lanmagan, CANCELLED/EXPIRED esa tushum emas).
    @Transactional(readOnly = true)
    public SubscriptionStatsDto getStats() {
        LocalDateTime now = LocalDateTime.now();
        YearMonth currentMonth = YearMonth.now();

        List<Subscription> confirmed = subscriptionRepository
                .findByStatusOrderByCreatedAtDesc(SubscriptionStatus.CONFIRMED);

        BigDecimal totalRevenue = confirmed.stream()
                .map(Subscription::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal thisMonthRevenue = confirmed.stream()
                .filter(s -> YearMonth.from(s.getCreatedAt()).equals(currentMonth))
                .map(Subscription::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Oy bo'yicha guruhlash — TreeMap avtomatik xronologik tartibda saqlaydi.
        DateTimeFormatter monthKeyFormat = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, MonthlyAccumulator> byMonth = new TreeMap<>();

        for (Subscription s : confirmed) {
            String key = YearMonth.from(s.getCreatedAt()).format(monthKeyFormat);
            byMonth.computeIfAbsent(key, k -> new MonthlyAccumulator())
                    .add(s.getAmount());
        }

        List<MonthlyRevenueDto> monthlyBreakdown = byMonth.entrySet().stream()
                .map(e -> MonthlyRevenueDto.builder()
                        .month(e.getKey())
                        .amount(e.getValue().total)
                        .count(e.getValue().count)
                        .build())
                .sorted(Comparator.comparing(MonthlyRevenueDto::month))
                .toList();

        long activeSubscribersCount = subscriptionRepository
                .countByStatusAndEndDateAfter(SubscriptionStatus.CONFIRMED, now);
        long pendingCount = subscriptionRepository.countByStatus(SubscriptionStatus.PENDING);

        return SubscriptionStatsDto.builder()
                .totalRevenue(totalRevenue)
                .thisMonthRevenue(thisMonthRevenue)
                .totalConfirmedCount(confirmed.size())
                .activeSubscribersCount(activeSubscribersCount)
                .pendingCount(pendingCount)
                .monthlyBreakdown(monthlyBreakdown)
                .build();
    }

    // /payments sahifasidagi hisobotni OWNER'ning o'z emailiga yuboradi.
    @Transactional(readOnly = true)
    public void emailStatsReport(User owner) {
        if (owner.getEmail() == null || owner.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "❌Sizda email manzil ulanmagan. Avval profilda emailingizni kiriting.");
        }

        SubscriptionStatsDto stats = getStats();
        boolean sent = emailService.sendSubscriptionReport(owner.getEmail(), stats);

        if (!sent) {
            throw new IllegalStateException("❌Hisobotni email orqali yuborishda xatolik yuz berdi.");
        }
    }

    // Oylik yig'indini hisoblash uchun ichki yordamchi (faqat getStats() ichida ishlatiladi).
    private static class MonthlyAccumulator {
        private BigDecimal total = BigDecimal.ZERO;
        private long count = 0;

        void add(BigDecimal amount) {
            total = total.add(amount);
            count++;
        }
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> listAll() {
        return subscriptionRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> listForUser(Long userId) {
        return subscriptionRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDto).toList();
    }

    // Har kuni 00:30'da ishga tushadi: muddati o'tgan CONFIRMED obunalarni
    // EXPIRED qiladi va, agar foydalanuvchida boshqa faol obuna qolmagan
    // bo'lsa, ROLE_ADMIN'ni olib tashlaydi.
    //
    // MUHIM: bu job faqat Subscription orqali berilgan ADMIN'larga tegadi.
    // Agar OWNER kimgadir /users sahifasidagi checkbox orqali (obunasiz)
    // to'g'ridan-to'g'ri ADMIN bergan bo'lsa, bu yerga umuman tushmaydi —
    // demak doimiy (obunasiz) ADMIN huquqlari avtomatik olib tashlanmaydi.
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void expireSubscriptions() {
        LocalDateTime now = LocalDateTime.now();

        List<Subscription> expired = subscriptionRepository
                .findByStatusAndEndDateBefore(SubscriptionStatus.CONFIRMED, now);

        for (Subscription subscription : expired) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);

            User user = subscription.getUser();
            boolean hasOtherActive = subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                    user.getId(), SubscriptionStatus.CONFIRMED, now);

            if (!hasOtherActive) {
                revokeAdmin(user);
                notificationService.create(user,
                        "⌛ ADMIN obunangiz muddati tugadi. Davom ettirish uchun to'lovni yangilang.",
                        "/profile");
                log.info("Obuna muddati tugadi, ROLE_ADMIN olib tashlandi: {}", user.getUsername());
            }
        }
    }

    private void grantAdmin(User user, User owner) {
        if (user.hasRole(ADMIN_ROLE)) return;

        Role adminRole = roleRepository.findByRoleName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN ma'lumotlar bazasida topilmadi"));

        user.getRoles().add(adminRole);
        userRepository.save(user);

        roleAuditService.record(user, owner, ADMIN_ROLE, RoleAuditAction.GRANTED, RoleAuditSource.SUBSCRIPTION);
    }

    // Avtomatik (inson ishtirokisiz) hollar uchun qisqa yo'l — scheduled
    // job (expireSubscriptions) va Click chargeback (reverseOnline).
    private void revokeAdmin(User user) {
        revokeAdmin(user, null, RoleAuditSource.SYSTEM);
    }

    // OWNER FAOL obunani qo'lda bekor qilganda ham shu metod ishlatiladi
    // (cancel()) — changedBy/source shu holatni audit tarixida to'g'ri
    // aks ettirish uchun aniq beriladi.
    private void revokeAdmin(User user, User changedBy, RoleAuditSource source) {
        if (!user.hasRole(ADMIN_ROLE)) return;

        // Xavfsizlik: foydalanuvchida kamida bitta rol qolishi shart.
        if (user.getRoles().size() <= 1) return;

        roleRepository.findByRoleName(ADMIN_ROLE).ifPresent(adminRole -> {
            user.getRoles().remove(adminRole);
            userRepository.save(user);

            roleAuditService.record(user, changedBy, ADMIN_ROLE, RoleAuditAction.REVOKED, source);
        });
    }

    private SubscriptionDto toDto(Subscription s) {
        return new SubscriptionDto(
                s.getId(),
                s.getUser().getId(),
                s.getUser().getUsername(),
                s.getAmount(),
                s.getSource().name(),
                s.getStatus().name(),
                s.getStartDate(),
                s.getEndDate(),
                s.getNote(),
                s.getCreatedAt()
        );
    }
}
