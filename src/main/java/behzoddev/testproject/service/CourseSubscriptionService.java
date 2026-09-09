package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.course.CourseSubscriptionDto;
import behzoddev.testproject.dto.course.CreateCourseSubscriptionDto;
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
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

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
                .build();
    }
}
