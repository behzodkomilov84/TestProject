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

    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
        // "yetim" qoldirmaymiz).
        CourseSubscription subscription = courseSubscriptionRepository
                .findByUser_IdAndCourse_IdAndStatus(user.getId(), courseId, CourseSubscriptionStatus.PENDING)
                .orElseGet(() -> CourseSubscription.builder().user(user).course(course).build());

        subscription.setAmount(amount);
        subscription.setStatus(CourseSubscriptionStatus.CONFIRMED);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(months));
        subscription.setNote("Onlayn to'lov (Click) orqali avtomatik tasdiqlandi");

        courseSubscriptionRepository.save(subscription);

        notificationService.create(user,
                "✅ \"" + course.getTitle() + "\" kursiga onlayn to'lov orqali obuna bo'ldingiz (" + months +
                        " oy)! Endi 1-bo'lim ochiq.",
                "/courses/" + courseId);

        log.info("Kurs obunasi (ONLINE) tasdiqlandi: user={}, course={}, muddat={} oy",
                user.getUsername(), course.getTitle(), months);

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

        subscription.setStatus(CourseSubscriptionStatus.CANCELLED);
        courseSubscriptionRepository.save(subscription);
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

            notificationService.create(subscription.getUser(),
                    "⌛ \"" + subscription.getCourse().getTitle() + "\" kursiga obunangiz muddati tugadi.",
                    "/courses/" + subscription.getCourse().getId());

            log.info("Kurs obunasi muddati tugadi: user={}, course={}",
                    subscription.getUser().getUsername(), subscription.getCourse().getTitle());
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
