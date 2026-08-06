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
import java.util.List;
import java.util.NoSuchElementException;

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

        // Barcha OWNER'larga xabar beramiz — "Kursga obuna berish" sahifasida
        // (barcha kurslar obunalari yagona joyda) so'rovni ko'rib, tasdiqlashlari uchun.
        for (User owner : userRepository.findByRoles_RoleName("ROLE_OWNER")) {
            notificationService.create(owner,
                    "🎓 " + user.getUsername() + " \"" + course.getTitle() + "\" kursiga obuna so'radi.",
                    "/courses/subscriptions?courseId=" + courseId);
        }

        log.info("Kurs obunasiga so'rov yuborildi: user={}, course={}", user.getUsername(), course.getTitle());
    }

    @Transactional
    public CourseSubscriptionDto subscribe(Long courseId, CreateCourseSubscriptionDto dto, User owner) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));

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

    @Transactional
    public void cancel(Long subscriptionId) {
        CourseSubscription subscription = courseSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Obuna topilmadi"));

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

    @Transactional(readOnly = true)
    public List<CourseSubscriptionDto> listForCourse(Long courseId) {
        return courseSubscriptionRepository.findByCourse_IdOrderByCreatedAtDesc(courseId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseSubscriptionDto> listAll() {
        return courseSubscriptionRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
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
