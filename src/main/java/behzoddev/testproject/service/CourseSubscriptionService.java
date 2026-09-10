package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.course.CourseSubscriptionDto;
import behzoddev.testproject.dto.course.CreateCourseSubscriptionDto;
import behzoddev.testproject.dto.subscription.MonthlyRevenueDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseSubscription;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

// Kursga muddatli kirish huquqi (ADMIN-rol obunasi bilan bir xil g'oyada
// — startDate/endDate). Ikki yo'l bilan boshlanishi mumkin: (1)
// foydalanuvchi "obuna bo'lishni xohlayman" so'rovini yuboradi (PENDING),
// OWNER buni ko'rib tasdiqlaydi; (2) OWNER to'g'ridan-to'g'ri (so'rovsiz)
// qo'lda obuna beradi. Ikkala holatda ham yakuniy tasdiqlash faqat
// OWNER tomonidan (hozircha Telegram orqali avtomatik oqim yo'q).
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSubscriptionService {

    private static final int DEFAULT_DURATION_MONTHS = 1;
    private static final int TRIAL_DURATION_DAYS = 3;

    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PaymentOrderRepository paymentOrderRepository;

    // "🎁 3 kunlik bepul sinov" — foydalanuvchi so'rovi, 2026-09-09:
    // avvalgi "Obunaga so'rov yuborish" (PENDING, OWNER tasdig'i kerak)
    // o'rniga endi shu TUGMA bosilganda OWNER kutmasdan, DARHOL 3
    // kunlik BEPUL kirish beriladi (subscribe()/confirmOnline() bilan
    // bir xil "CONFIRMED" holat — shu sabab mavjud expireSubscriptions()
    // kunlik job'i muddat tugaganda AVTOMATIK ravishda EXPIRED qilib,
    // bildirishnoma yuboradi — alohida yangi mexanizm shart emas).
    // Bitta foydalanuvchi bitta kursda FAQAT BIR MARTA sinovdan
    // foydalana oladi (CourseSubscription.trial bilan nazorat qilinadi).
    @Transactional
    public CourseSubscriptionDto startFreeTrial(Long courseId, User user) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));

        if (course.isFree()) {
            throw new IllegalArgumentException("❌Bu kurs allaqachon bepul — sinovga hojat yo'q");
        }

        if (courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                user.getId(), courseId, CourseSubscriptionStatus.CONFIRMED, LocalDateTime.now())) {
            throw new IllegalArgumentException("❌Siz allaqachon shu kursga obuna bo'lgansiz");
        }

        if (courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndTrialTrue(user.getId(), courseId)) {
            throw new IllegalArgumentException("❌Bu kurs uchun bepul sinov muddatingiz allaqachon ishlatilgan");
        }

        LocalDateTime now = LocalDateTime.now();

        CourseSubscription subscription = CourseSubscription.builder()
                .user(user)
                .course(course)
                .amount(BigDecimal.ZERO)
                .status(CourseSubscriptionStatus.CONFIRMED)
                .startDate(now)
                .endDate(now.plusDays(TRIAL_DURATION_DAYS))
                .trial(true)
                .note("🎁 3 kunlik bepul sinov (bonus)")
                .build();

        courseSubscriptionRepository.save(subscription);

        // Matn aniqlik uchun tuzatildi (foydalanuvchi so'rovi, 2026-09-09:
        // "Хабар берамиз эмас тўловга қадар блокланади дейиш керак") —
        // "xabar beramiz" chalg'ituvchi edi, aslida muddat tugagach kurs
        // TO'LOVGA QADAR yopiladi (kirish avtomatik bloklanadi).
        notificationService.create(user,
                "🎁 \"" + course.getTitle() + "\" kursidan " + TRIAL_DURATION_DAYS +
                        " kun BEPUL foydalanishingiz mumkin! Muddati tugagach, to'lov qilmaguningizcha kursga kirish bloklanadi.",
                "/courses/" + courseId);

        log.info("Kursga bepul sinov berildi: user={}, course={}, {} kun",
                user.getUsername(), course.getTitle(), TRIAL_DURATION_DAYS);

        return toDto(subscription);
    }

    // Foydalanuvchi kurs sahifasida "Obunaga so'rov yuborish" tugmasini
    // bosganda chaqiriladi — hali to'lov summasi yo'q, OWNER buni
    // ko'rib chiqib, summani belgilab tasdiqlaydi (subscribe()).
    @Transactional
    public void requestSubscription(Long courseId, User user) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));

        if (courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                user.getId(), courseId, CourseSubscriptionStatus.CONFIRMED, LocalDateTime.now())) {
            throw new IllegalArgumentException("❌Siz allaqachon shu kursga obuna bo'lgansiz");
        }

        if (courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatus(
                user.getId(), courseId, CourseSubscriptionStatus.PENDING)) {
            throw new IllegalArgumentException("❌So'rovingiz allaqachon yuborilgan, OWNER javobini kuting");
        }

        CourseSubscription request = CourseSubscription.builder()
                .user(user)
                .course(course)
                .amount(BigDecimal.ZERO)
                .status(CourseSubscriptionStatus.PENDING)
                .build();

        courseSubscriptionRepository.save(request);

        // Barcha ROLE_OWNER'larga xabar beramiz — ular cheklovsiz, HAR QANDAY
        // kursning so'rovini ko'rishi kerak ("Kursga obuna berish" sahifasida,
        // barcha kurslar obunalari yagona joyda). Bundan tashqari, kursning
        // muallifiga (createdBy) HAM alohida xabar beramiz — u ROLE_ADMIN
        // bo'lsa (o'z kursini yaratgan o'qituvchi), ilgari umuman
        // bildirishnoma olmasdi; ROLE_OWNER bo'lsa, pastdagi tsiklda
        // allaqachon xabar olgan bo'ladi — takroriy yubormaslik uchun
        // "notified" to'plami bilan nazorat qilinadi (foydalanuvchi so'rovi,
        // 2026-09-07: "билдиришномалар фақат шу админнинг ўзига келсин.
        // OWNER учун чеклов йўқ").
        // Link'da courseId bilan birga userId ham beriladi — shunda
        // bildirishnomani bosganda sahifada kurs HAM, so'ragan foydalanuvchi
        // HAM oldindan tanlangan holda ochiladi (qo'lda qidirish shart emas).
        Set<Long> notified = new HashSet<>();
        for (User owner : userRepository.findByRoles_RoleName("ROLE_OWNER")) {
            notificationService.create(owner,
                    "🎓 " + user.getUsername() + " \"" + course.getTitle() + "\" kursiga obuna so'radi.",
                    "/courses/subscriptions?courseId=" + courseId + "&userId=" + user.getId());
            notified.add(owner.getId());
        }

        User author = course.getCreatedBy();
        if (author != null && !notified.contains(author.getId())) {
            notificationService.create(author,
                    "🎓 " + user.getUsername() + " \"" + course.getTitle() + "\" kursiga obuna so'radi.",
                    "/courses/subscriptions?courseId=" + courseId + "&userId=" + user.getId());
        }

        log.info("Kurs obunasiga so'rov yuborildi: user={}, course={}", user.getUsername(), course.getTitle());
    }

    @Transactional
    public CourseSubscriptionDto subscribe(Long courseId, CreateCourseSubscriptionDto dto, User owner) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));
        // ROLE_ADMIN faqat o'zi yaratgan kursga obuna berishi/tasdiqlashi
        // mumkin — ROLE_OWNER cheklovsiz (foydalanuvchi so'rovi, 2026-09-07).
        checkCanManage(course, owner);

        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new IllegalArgumentException("❌Foydalanuvchi topilmadi"));

        if (dto.amount() == null || dto.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("❌To'lov summasi noto'g'ri");
        }

        if (courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                user.getId(), courseId, CourseSubscriptionStatus.CONFIRMED, LocalDateTime.now())) {
            throw new IllegalArgumentException("❌Bu foydalanuvchi allaqachon shu kursga obuna bo'lgan");
        }

        int months = dto.durationMonths() == null || dto.durationMonths() <= 0
                ? DEFAULT_DURATION_MONTHS
                : dto.durationMonths();
        LocalDateTime now = LocalDateTime.now();

        // Agar oldindan PENDING so'rov bo'lsa — o'shani tasdiqlaymiz
        // (yangi qator yaratib, eskisini "yetim" qoldirmaymiz).
        CourseSubscription subscription = courseSubscriptionRepository
                .findByUser_IdAndCourse_IdAndStatus(user.getId(), courseId, CourseSubscriptionStatus.PENDING)
                .orElseGet(() -> CourseSubscription.builder().user(user).course(course).build());

        subscription.setAmount(dto.amount());
        subscription.setStatus(CourseSubscriptionStatus.CONFIRMED);
        subscription.setConfirmedBy(owner);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(months));
        subscription.setNote(dto.note());

        courseSubscriptionRepository.save(subscription);

        notificationService.create(user,
                "🎓 \"" + course.getTitle() + "\" kursiga obuna bo'ldingiz (" + months + " oy)! Endi 1-bo'lim ochiq.",
                "/courses/" + courseId);

        log.info("Kurs obunasi tasdiqlandi: user={}, course={}, muddat={} oy, owner={}",
                user.getUsername(), course.getTitle(), months, owner.getUsername());

        return toDto(subscription);
    }

    // PaymentOrderService.markPaid() dan chaqiriladi (Click Complete
    // muvaffaqiyatli bo'lganda) — subscribe() bilan bir xil g'oyada, lekin
    // OWNER emas, tizimning o'zi tasdiqlaydi ("Onlayn to'lov" izohi bilan).
    @Transactional
    public CourseSubscriptionDto confirmOnline(User user, Long courseId, BigDecimal amount, int months) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));

        LocalDateTime now = LocalDateTime.now();

        // Agar oldindan PENDING so'rov bo'lsa — o'shani tasdiqlaymiz
        // (subscribe() bilan bir xil — yangi qator yaratib, eskisini
        // "yetim" qoldirmaymiz). SHUNINGDEK: agar hozir FAOL "🎁 bepul
        // sinov" bo'lsa — o'sha QATORNI TO'LOVGA "yangilaymiz" (yangi
        // qator yaratmaymiz) — aks holda ikkita CONFIRMED qator qolib
        // ketardi va eski sinov qatori kunlik job orqali keyinroq
        // "EXPIRED" bo'lib, allaqachon to'lagan foydalanuvchiga
        // chalg'ituvchi "sinov tugadi" xabari yuborardi (haqiqiy topilgan
        // bug, foydalanuvchi so'rovi, 2026-09-09).
        CourseSubscription subscription = courseSubscriptionRepository
                .findByUser_IdAndCourse_IdAndStatus(user.getId(), courseId, CourseSubscriptionStatus.PENDING)
                .or(() -> courseSubscriptionRepository
                        .findByUser_IdAndCourse_IdAndStatus(user.getId(), courseId, CourseSubscriptionStatus.CONFIRMED)
                        .filter(CourseSubscription::isTrial))
                .orElseGet(() -> CourseSubscription.builder().user(user).course(course).build());

        // "Bonus kunlar" — sinov FAOL paytida to'lansa, undan qolgan
        // kunlar to'langan muddatga QO'SHIB beriladi, yo'qolib ketmaydi
        // (foydalanuvchi so'rovi, 2026-09-09: "тўлаган суммасига кўра
        // берилган муддатга бонус кунлари ҳам қўшиб берилсин"). Status
        // o'zgartirilishidan OLDIN hisoblanadi — pastda subscription
        // endDate/trial allaqachon qayta yozilgan bo'lmasin.
        long bonusDays = 0;
        if (subscription.isTrial() && subscription.getEndDate() != null && subscription.getEndDate().isAfter(now)) {
            bonusDays = java.time.Duration.between(now, subscription.getEndDate()).toDays();
            // To'liq kunga yetmagan (masalan 5 soat) qolgan vaqt ham
            // yo'qolib ketmasligi kerak — hech bo'lmasa 1 kun beriladi.
            if (bonusDays <= 0) {
                bonusDays = 1;
            }
        }

        subscription.setAmount(amount);
        subscription.setStatus(CourseSubscriptionStatus.CONFIRMED);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(months).plusDays(bonusDays));
        subscription.setNote(bonusDays > 0
                ? "Onlayn to'lov (Click) orqali avtomatik tasdiqlandi (+" + bonusDays + " kun sinov bonusi)"
                : "Onlayn to'lov (Click) orqali avtomatik tasdiqlandi");
        subscription.setTrial(false);

        courseSubscriptionRepository.save(subscription);

        String bonusText = bonusDays > 0 ? " + " + bonusDays + " kun bonus" : "";
        notificationService.create(user,
                "✅ \"" + course.getTitle() + "\" kursiga onlayn to'lov orqali obuna bo'ldingiz (" + months +
                        " oy" + bonusText + ")! Endi 1-bo'lim ochiq.",
                "/courses/" + courseId);

        log.info("Kurs obunasi (ONLINE) tasdiqlandi: user={}, course={}, muddat={} oy, bonus={} kun",
                user.getUsername(), course.getTitle(), months, bonusDays);

        return toDto(subscription);
    }

    // Click chargeback/qaytarish (Complete'dan KEYIN "error<0" bilan
    // qayta kelsa) — SubscriptionService.reverseOnline bilan bir xil g'oya:
    // aynan shu to'lov bilan tasdiqlangan obunani bekor qiladi.
    @Transactional
    public void reverseOnline(Long courseSubscriptionId) {
        if (courseSubscriptionId == null) return;

        courseSubscriptionRepository.findById(courseSubscriptionId).ifPresent(s -> {
            if (s.getStatus() != CourseSubscriptionStatus.CONFIRMED) return;

            s.setStatus(CourseSubscriptionStatus.CANCELLED);
            courseSubscriptionRepository.save(s);

            notificationService.create(s.getUser(),
                    "⚠️ \"" + s.getCourse().getTitle() + "\" kursiga onlayn to'lovingiz bekor qilindi (qaytarildi), " +
                            "shunga ko'ra kursga kirish huquqi ham bekor qilindi.",
                    "/courses/" + s.getCourse().getId());

            log.info("Kurs obunasi (ONLINE) bekor qilindi: user={}, course={}",
                    s.getUser().getUsername(), s.getCourse().getTitle());
        });
    }

    @Transactional
    public void cancel(Long subscriptionId, User requester) {
        CourseSubscription subscription = courseSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Obuna topilmadi"));
        // ROLE_ADMIN faqat o'zi yaratgan kursning obunasini bekor qilishi/
        // rad etishi mumkin — ROLE_OWNER cheklovsiz (foydalanuvchi so'rovi, 2026-09-07).
        checkCanManage(subscription.getCourse(), requester);

        // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09:
        // "Foydalanuvchini obunasi rad etildi, lekin bildirishnomaga
        // kelmadi USER ga") — bu metod PENDING so'rovni ("rad etish") VA
        // CONFIRMED obunani ("bekor qilish") bir xil holatga o'tkazadi,
        // lekin foydalanuvchiga hech qachon xabar bermas edi. Status
        // o'zgartirilishidan OLDIN saqlab qo'yiladi — matn shunga qarab
        // to'g'ri so'z bilan ("rad etildi" / "bekor qilindi") tanlanadi.
        boolean wasPending = subscription.getStatus() == CourseSubscriptionStatus.PENDING;

        subscription.setStatus(CourseSubscriptionStatus.CANCELLED);
        courseSubscriptionRepository.save(subscription);

        String message = wasPending
                ? "❌ \"" + subscription.getCourse().getTitle() + "\" kursiga obuna so'rovingiz administrator tomonidan rad etildi."
                : "⚠️ \"" + subscription.getCourse().getTitle() + "\" kursiga obunangiz administrator tomonidan bekor qilindi.";

        notificationService.create(subscription.getUser(), message, "/courses/" + subscription.getCourse().getId());

        log.info("Kurs obunasi {}: user={}, course={}, requester={}",
                wasPending ? "rad etildi" : "bekor qilindi",
                subscription.getUser().getUsername(), subscription.getCourse().getTitle(), requester.getUsername());
    }

    // "/courses/subscriptions" sahifasidagi "✏️ Tahrirlash" — mavjud
    // obunaning summasi/muddatini o'zgartiradi (foydalanuvchi so'rovi,
    // 2026-09-09: "Edit ni ham qo'sh, muddatini o'zgartirishimiz mumkin").
    // CANCELLED/EXPIRED obunani tahrirlash uni QAYTA FAOLlashtiradi —
    // bu OWNER uchun "adashib bekor qilingan/eskirgan obunani tuzatish"
    // amaliy stsenariysini alohida "qayta tiklash" tugmasisiz yopadi.
    @Transactional
    public CourseSubscriptionDto updateSubscription(Long subscriptionId, BigDecimal amount, Integer durationMonths, User requester) {
        CourseSubscription subscription = courseSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Obuna topilmadi"));
        checkCanManage(subscription.getCourse(), requester);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("❌To'lov summasi noto'g'ri");
        }
        if (durationMonths == null || durationMonths <= 0) {
            throw new IllegalArgumentException("❌Muddat noto'g'ri");
        }

        LocalDateTime startDate = subscription.getStartDate() != null ? subscription.getStartDate() : LocalDateTime.now();
        subscription.setAmount(amount);
        subscription.setStartDate(startDate);
        subscription.setEndDate(startDate.plusMonths(durationMonths));
        subscription.setStatus(CourseSubscriptionStatus.CONFIRMED);
        subscription.setTrial(false);
        // "Manba" ustunida to'g'ri "✋ Qo'lda berilgan" ko'rsatilishi
        // uchun (foydalanuvchi so'rovi, 2026-09-09) — bu OWNER/ADMIN'ning
        // o'zi bajargan qo'lda amal, avvalgi manba (masalan onlayn to'lov)
        // endi eskirgan hisoblanadi.
        subscription.setConfirmedBy(requester);

        courseSubscriptionRepository.save(subscription);

        notificationService.create(subscription.getUser(),
                "✏️ \"" + subscription.getCourse().getTitle() + "\" kursidagi obunangiz administrator tomonidan " +
                        "yangilandi (" + durationMonths + " oy).",
                "/courses/" + subscription.getCourse().getId());

        log.info("Kurs obunasi tahrirlandi: id={}, user={}, course={}, muddat={} oy, requester={}",
                subscriptionId, subscription.getUser().getUsername(), subscription.getCourse().getTitle(),
                durationMonths, requester.getUsername());

        return toDto(subscription);
    }

    // "/courses/subscriptions" sahifasidagi "🗑️ O'chirish" — "Bekor
    // qilish"dan (cancel()) farqli, yozuvni butunlay o'chiradi (soft
    // CANCELLED holatga o'tkazish emas). Foydalanuvchiga xabar YUBORILMAYDI
    // — bu ko'proq eski/xato yozuvlarni tozalash uchun ma'muriy amal,
    // kirish huquqiga ta'sir qiladigan qaror emas (agar hali CONFIRMED
    // bo'lsa ham, xohlagan holatda o'chirish OWNER'ning o'zi tanlagan
    // ataylab qilingan tozalash harakati hisoblanadi).
    @Transactional
    public void delete(Long subscriptionId, User requester) {
        CourseSubscription subscription = courseSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Obuna topilmadi"));
        checkCanManage(subscription.getCourse(), requester);

        // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-10: Click
        // panelida to'lov "muvaffaqiyatli" ko'rinsa-da, /payments'da
        // butunlay yo'q edi) — "subscription_id" haqiqiy FK EMAS, shuning
        // uchun bu yozuv o'chirilganda unga ishora qiluvchi PaymentOrder
        // "osilib qolgan" (dangling) havola bilan qolib ketardi
        // (SubscriptionService.delete bilan bir xil tuzatish).
        paymentOrderRepository.clearSubscriptionId(subscriptionId);

        courseSubscriptionRepository.delete(subscription);

        log.info("Kurs obunasi butunlay o'chirildi: id={}, user={}, course={}, requester={}",
                subscriptionId, subscription.getUser().getUsername(), subscription.getCourse().getTitle(),
                requester.getUsername());
    }

    // Har kuni 00:35'da ishga tushadi (SubscriptionService.expireSubscriptions
    // bilan bir xil pattern, faqat bir oz boshqa vaqtda — bir vaqtda ikkita
    // job MySQL'ga urilib qolmasligi uchun): muddati o'tgan CONFIRMED kurs
    // obunalarini EXPIRED qiladi.
    @Scheduled(cron = "0 35 0 * * *")
    @Transactional
    public void expireSubscriptions() {
        LocalDateTime now = LocalDateTime.now();

        List<CourseSubscription> expired = courseSubscriptionRepository
                .findByStatusAndEndDateBefore(CourseSubscriptionStatus.CONFIRMED, now);

        for (CourseSubscription subscription : expired) {
            subscription.setStatus(CourseSubscriptionStatus.EXPIRED);
            courseSubscriptionRepository.save(subscription);

            // Bepul sinov (trial) tugagandan keyingi xabar — to'lovga
            // yo'naltiruvchi alohida matn bilan (foydalanuvchi so'rovi,
            // 2026-09-09: "Бонус кун тугаса автомат ҳабар бериши керак...
            // тўлов саҳифасига олиб бориши керак"). Link kurs sahifasiga
            // olib boradi — o'sha yerda to'lov modali (1/3/6 oy, chegirma
            // bilan) avtomatik ko'rinadi, chunki obuna endi mavjud emas.
            String message = subscription.isTrial()
                    ? "⌛ \"" + subscription.getCourse().getTitle() + "\" kursidagi 3 kunlik BEPUL sinovingiz tugadi. " +
                            "Davom etish uchun endi to'lov qilishingiz mumkin."
                    : "⌛ \"" + subscription.getCourse().getTitle() + "\" kursiga obunangiz muddati tugadi.";

            notificationService.create(subscription.getUser(), message,
                    "/courses/" + subscription.getCourse().getId());

            log.info("Kurs obunasi muddati tugadi: user={}, course={}, trial={}",
                    subscription.getUser().getUsername(), subscription.getCourse().getTitle(), subscription.isTrial());
        }
    }

    // requester — ROLE_OWNER cheklovsiz istalgan kursni ko'radi; ROLE_ADMIN
    // faqat O'ZI yaratgan kursni (foydalanuvchi so'rovi, 2026-09-07:
    // "билдиришномалар фақат шу админнинг ўзига келсин. OWNER учун чеклов йўқ").
    @Transactional(readOnly = true)
    public List<CourseSubscriptionDto> listForCourse(Long courseId, User requester) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));
        checkCanManage(course, requester);

        return courseSubscriptionRepository.findByCourse_IdOrderByCreatedAtDesc(courseId)
                .stream().map(this::toDto).toList();
    }

    // ROLE_OWNER — barcha kurslarning obunalari; ROLE_ADMIN — faqat o'zi
    // yaratgan kurslarnikini ko'radi (boshqa muallif/adminlarning
    // obunachilari ro'yxati ko'rinmasligi kerak).
    @Transactional(readOnly = true)
    public List<CourseSubscriptionDto> listAll(User requester) {
        List<CourseSubscription> all = courseSubscriptionRepository.findAllByOrderByCreatedAtDesc();

        if (requester.hasRole("ROLE_OWNER")) {
            return all.stream().map(this::toDto).toList();
        }

        return all.stream()
                .filter(s -> canManageCourse(s.getCourse(), requester))
                .map(this::toDto)
                .toList();
    }

    // CourseService#canManageCourse bilan bir xil qoida: ROLE_OWNER —
    // cheklovsiz, ROLE_ADMIN — faqat o'zi yaratgan kurs.
    private boolean canManageCourse(Course course, User user) {
        return user.hasRole("ROLE_OWNER")
                || (course.getCreatedBy() != null && course.getCreatedBy().getId().equals(user.getId()));
    }

    private void checkCanManage(Course course, User requester) {
        if (!canManageCourse(course, requester)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "⛔ Faqat o'zingiz yaratgan kursning obunalarini boshqarishingiz mumkin.");
        }
    }

    // "/payments" (to'lov tarixi) sahifasidagi umumiy ko'rsatkichlar —
    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09: "/payments
    // ma'lumotlari noto'g'ri" — Click'ning o'z panelida 151 000 so'm
    // ko'rinsa-da, saytimizdagi sahifa faqat 50 000 so'mni ko'rsatardi).
    // Sabab: /payments FAQAT umumiy Subscription (ADMIN-rol) yozuvlarini
    // hisoblardi — kurs obunalari uchun to'lovlar (CourseSubscription,
    // masalan "Bakteriologiya" kursiga 2 oylik 100 000 so'm) butunlay
    // hisobga olinmasdi. SubscriptionService.getStats() bilan bir xil
    // hisoblash mantig'i, faqat CourseSubscription uchun — natijalar
    // /payments'da ikkalasi birlashtirilib ko'rsatiladi.
    @Transactional(readOnly = true)
    public SubscriptionStatsDto getStats() {
        LocalDateTime now = LocalDateTime.now();
        YearMonth currentMonth = YearMonth.now();

        List<CourseSubscription> all = courseSubscriptionRepository.findAllByOrderByCreatedAtDesc();

        List<CourseSubscription> confirmed = all.stream()
                .filter(s -> s.getStatus() == CourseSubscriptionStatus.CONFIRMED)
                .toList();

        // "🎁 Bepul sinov" — bonus, HAQIQIY TO'LOV EMAS (amount=0), shuning
        // uchun "to'lovlar soni" statistikasidan chiqarib tashlanadi
        // (foydalanuvchi so'rovi, 2026-09-09: "bonuslarni to'lovlar soniga
        // qo'shma"). Tushum (revenue) hisobiga ta'siri yo'q — trial'ning
        // summasi baribir 0, faqat SON noto'g'ri shishib ko'rinardi.
        List<CourseSubscription> paidConfirmed = confirmed.stream()
                .filter(s -> !s.isTrial())
                .toList();

        BigDecimal totalRevenue = paidConfirmed.stream()
                .map(CourseSubscription::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal thisMonthRevenue = paidConfirmed.stream()
                .filter(s -> YearMonth.from(s.getCreatedAt()).equals(currentMonth))
                .map(CourseSubscription::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DateTimeFormatter monthKeyFormat = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, MonthlyAccumulator> byMonth = new TreeMap<>();
        for (CourseSubscription s : paidConfirmed) {
            String key = YearMonth.from(s.getCreatedAt()).format(monthKeyFormat);
            byMonth.computeIfAbsent(key, k -> new MonthlyAccumulator()).add(s.getAmount());
        }

        List<MonthlyRevenueDto> monthlyBreakdown = byMonth.entrySet().stream()
                .map(e -> MonthlyRevenueDto.builder()
                        .month(e.getKey())
                        .amount(e.getValue().total)
                        .count(e.getValue().count)
                        .build())
                .sorted(Comparator.comparing(MonthlyRevenueDto::month))
                .toList();

        // "Faol obunachilar" — bu "to'lov" emas, "hozir kirish huquqi
        // bormi" degan ko'rsatkich, shuning uchun trial foydalanuvchilar
        // HAM shu yerga kiradi (ular ham hozir haqiqatan faol).
        long activeSubscribersCount = confirmed.stream()
                .filter(s -> s.getEndDate() != null && s.getEndDate().isAfter(now))
                .count();
        long pendingCount = all.stream()
                .filter(s -> s.getStatus() == CourseSubscriptionStatus.PENDING)
                .count();

        return SubscriptionStatsDto.builder()
                .totalRevenue(totalRevenue)
                .thisMonthRevenue(thisMonthRevenue)
                .totalConfirmedCount(paidConfirmed.size())
                .activeSubscribersCount(activeSubscribersCount)
                .pendingCount(pendingCount)
                .monthlyBreakdown(monthlyBreakdown)
                .build();
    }

    // SubscriptionService.MonthlyAccumulator bilan bir xil — oylik
    // yig'indini hisoblash uchun ichki yordamchi (faqat getStats() ichida).
    private static class MonthlyAccumulator {
        private BigDecimal total = BigDecimal.ZERO;
        private long count = 0;

        void add(BigDecimal amount) {
            total = total.add(amount);
            count++;
        }
    }

    private CourseSubscriptionDto toDto(CourseSubscription s) {
        return CourseSubscriptionDto.builder()
                .id(s.getId())
                .userId(s.getUser().getId())
                .username(s.getUser().getUsername())
                .courseId(s.getCourse().getId())
                .courseTitle(s.getCourse().getTitle())
                .amount(s.getAmount())
                .status(s.getStatus().name())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .note(s.getNote())
                .createdAt(s.getCreatedAt())
                .source(subscriptionSource(s))
                .build();
    }

    // CourseSubscription'da alohida "source" ustuni yo'q — mavjud
    // confirmedBy/trial maydonlaridan hisoblanadi: trial=true bo'lsa
    // bepul sinov; confirmedBy to'ldirilgan bo'lsa OWNER/ADMIN qo'lda
    // tasdiqlagan (subscribe()); aks holda (ikkalasi ham yo'q) Click
    // orqali avtomatik (confirmOnline() confirmedBy'ni HECH QACHON
    // to'ldirmaydi — inson ishtirok etmagani uchun).
    private String subscriptionSource(CourseSubscription s) {
        // PENDING — foydalanuvchining o'zi "obuna berishni so'rayman"
        // so'rovi, hali hech kim tasdiqlamagan — "ONLINE" deb ko'rsatish
        // noto'g'ri bo'lardi.
        if (s.getStatus() == CourseSubscriptionStatus.PENDING) return "REQUESTED";
        if (s.isTrial()) return "TRIAL";
        if (s.getConfirmedBy() != null) return "MANUAL";
        return "ONLINE";
    }
}
