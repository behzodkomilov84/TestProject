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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

// Kursga (muddatsiz) kirish huquqini OWNER qo'lda qayd qilib, darhol
// tasdiqlaydi — ADMIN-rol obunasidan farqli, bu yerda Telegram orqali
// so'rov oqimi yo'q (hozircha).
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSubscriptionService {

    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public CourseSubscriptionDto subscribe(Long courseId, CreateCourseSubscriptionDto dto, User owner) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));

        User user = userRepository.findById(dto.userId())
                .orElseThrow(() -> new IllegalArgumentException("❌Foydalanuvchi topilmadi"));

        if (dto.amount() == null || dto.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("❌To'lov summasi noto'g'ri");
        }

        if (courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatus(
                user.getId(), courseId, CourseSubscriptionStatus.CONFIRMED)) {
            throw new IllegalArgumentException("❌Bu foydalanuvchi allaqachon shu kursga obuna bo'lgan");
        }

        CourseSubscription subscription = CourseSubscription.builder()
                .user(user)
                .course(course)
                .amount(dto.amount())
                .status(CourseSubscriptionStatus.CONFIRMED)
                .confirmedBy(owner)
                .note(dto.note())
                .build();

        courseSubscriptionRepository.save(subscription);

        notificationService.create(user,
                "🎓 \"" + course.getTitle() + "\" kursiga obuna bo'ldingiz! Endi 1-bo'lim ochiq.",
                "/courses/" + courseId);

        log.info("Kurs obunasi tasdiqlandi: user={}, course={}, owner={}",
                user.getUsername(), course.getTitle(), owner.getUsername());

        return toDto(subscription);
    }

    @Transactional
    public void cancel(Long subscriptionId) {
        CourseSubscription subscription = courseSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Obuna topilmadi"));

        subscription.setStatus(CourseSubscriptionStatus.CANCELLED);
        courseSubscriptionRepository.save(subscription);
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
                .note(s.getNote())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
