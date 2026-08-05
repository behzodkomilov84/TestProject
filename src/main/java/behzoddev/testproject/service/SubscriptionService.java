package behzoddev.testproject.service;

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
 * - ONLINE: hozircha faqat enum sifatida tayyorlab qo'yilgan — Payme/Click
 *   kabi shlyuz ulanganda shu manba orqali avtomatik tasdiqlash qo'shiladi.
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

    // Telegram bot orqali kelgan to'lov so'rovi — hali tasdiqlanmagan holatda
    // yaratiladi, OWNER /users sahifasida ko'rib chiqib tasdiqlaydi/rad etadi.
    @Transactional
    public SubscriptionDto createPendingFromTelegram(Long telegramId, BigDecimal amount) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Foydalanuvchi topilmadi. Avval saytda Telegramni ulang."));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("To'lov summasi noto'g'ri");
        }

        Subscription subscription = Subscription.builder()
                .user(user)
                .amount(amount)
                .source(SubscriptionSource.TELEGRAM)
                .status(SubscriptionStatus.PENDING)
                .build();

        subscriptionRepository.save(subscription);

        log.info("Obuna so'rovi (TELEGRAM) yaratildi: user={}, summa={}", user.getUsername(), amount);

        return toDto(subscription);
    }

    @Transactional
    public SubscriptionDto confirm(Long subscriptionId, Integer durationMonths, User owner) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("So'rov topilmadi"));

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new IllegalArgumentException("Bu so'rov allaqachon ko'rib chiqilgan");
        }

        int months = durationMonths == null ? DEFAULT_DURATION_MONTHS : durationMonths;
        LocalDateTime now = LocalDateTime.now();

        subscription.setStatus(SubscriptionStatus.CONFIRMED);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(months));
        subscription.setConfirmedBy(owner);

        grantAdmin(subscription.getUser(), owner);

        notificationService.create(subscription.getUser(),
                "✅ ADMIN huquqingiz tasdiqlandi! Endi " + months + " oy davomida o'qituvchi sifatida ishlashingiz mumkin.",
                "/profile");

        log.info("Obuna so'rovi tasdiqlandi: user={}, muddat={} oy, owner={}",
                subscription.getUser().getUsername(), months, owner.getUsername());

        return toDto(subscription);
    }

    @Transactional
    public SubscriptionDto cancel(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("So'rov topilmadi"));

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new IllegalArgumentException("Faqat kutilayotgan so'rovni bekor qilish mumkin");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        return toDto(subscription);
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
    public List<SubscriptionDto> listPending() {
        return subscriptionRepository.findByStatusOrderByCreatedAtDesc(SubscriptionStatus.PENDING)
                .stream().map(this::toDto).toList();
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

    // Faqat scheduled job (expireSubscriptions) orqali chaqiriladi — inson
    // ishtirok etmagani uchun changedBy=null, source=SYSTEM.
    private void revokeAdmin(User user) {
        if (!user.hasRole(ADMIN_ROLE)) return;

        // Xavfsizlik: foydalanuvchida kamida bitta rol qolishi shart.
        if (user.getRoles().size() <= 1) return;

        roleRepository.findByRoleName(ADMIN_ROLE).ifPresent(adminRole -> {
            user.getRoles().remove(adminRole);
            userRepository.save(user);

            roleAuditService.record(user, null, ADMIN_ROLE, RoleAuditAction.REVOKED, RoleAuditSource.SYSTEM);
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
