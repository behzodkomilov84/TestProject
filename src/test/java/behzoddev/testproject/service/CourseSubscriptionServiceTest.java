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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseSubscriptionServiceTest {

    @Mock
    private CourseSubscriptionRepository courseSubscriptionRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CourseSubscriptionService courseSubscriptionService;

    private Course course;
    private User student;
    private User owner;

    @BeforeEach
    void setUp() {
        course = Course.builder().id(1L).title("Java Asoslari").build();
        student = User.builder().id(1L).username("student1").build();
        owner = User.builder().id(99L).username("owner").build();
    }

    // ===== requestSubscription =====

    @Test
    void requestSubscription_success_notifiesAllOwnersWithDeepLink() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(false);
        when(userRepository.findByRoles_RoleName("ROLE_OWNER")).thenReturn(List.of(owner));

        courseSubscriptionService.requestSubscription(1L, student);

        ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);
        verify(courseSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CourseSubscriptionStatus.PENDING);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(notificationService).create(eq(owner), anyString(),
                eq("/courses/subscriptions?courseId=1&userId=1"));
    }

    @Test
    void requestSubscription_courseNotFound_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.requestSubscription(1L, student))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void requestSubscription_alreadyActiveSubscription_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);

        assertThatThrownBy(() -> courseSubscriptionService.requestSubscription(1L, student))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon shu kursga obuna bo'lgansiz");

        verify(courseSubscriptionRepository, never()).save(any());
    }

    @Test
    void requestSubscription_pendingRequestAlreadyExists_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> courseSubscriptionService.requestSubscription(1L, student))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon yuborilgan");
    }

    // ===== subscribe =====

    @Test
    void subscribe_newRequest_createsConfirmedSubscription() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.valueOf(100_000), 2, "izoh");

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.empty());

        CourseSubscriptionDto result = courseSubscriptionService.subscribe(1L, dto, owner);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);
        verify(courseSubscriptionRepository).save(captor.capture());
        assertThat(Period.between(captor.getValue().getStartDate().toLocalDate(),
                captor.getValue().getEndDate().toLocalDate()).toTotalMonths()).isEqualTo(2);
        verify(notificationService).create(eq(student), anyString(), eq("/courses/1"));
    }

    @Test
    void subscribe_existingPendingRequest_confirmsSameRowInsteadOfCreatingNew() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.valueOf(50_000), 1, null);
        CourseSubscription pending = CourseSubscription.builder().id(5L).user(student).course(course)
                .amount(BigDecimal.ZERO).status(CourseSubscriptionStatus.PENDING).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.of(pending));

        CourseSubscriptionDto result = courseSubscriptionService.subscribe(1L, dto, owner);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(pending.getStatus()).isEqualTo(CourseSubscriptionStatus.CONFIRMED);
        verify(courseSubscriptionRepository, times(1)).save(pending);
    }

    @Test
    void subscribe_nullOrNonPositiveDuration_defaultsToOneMonth() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.valueOf(50_000), 0, null);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.empty());

        courseSubscriptionService.subscribe(1L, dto, owner);

        ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);
        verify(courseSubscriptionRepository).save(captor.capture());
        assertThat(Period.between(captor.getValue().getStartDate().toLocalDate(),
                captor.getValue().getEndDate().toLocalDate()).toTotalMonths()).isEqualTo(1);
    }

    @Test
    void subscribe_invalidAmount_throws() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.valueOf(-1), 1, null);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> courseSubscriptionService.subscribe(1L, dto, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("To'lov summasi noto'g'ri");
    }

    @Test
    void subscribe_courseNotFound_throws() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.TEN, 1, null);
        when(courseRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.subscribe(1L, dto, owner))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void subscribe_userNotFound_throws() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.TEN, 1, null);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.subscribe(1L, dto, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Foydalanuvchi topilmadi");
    }

    @Test
    void subscribe_alreadyActiveSubscription_throws() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.TEN, 1, null);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);

        assertThatThrownBy(() -> courseSubscriptionService.subscribe(1L, dto, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon shu kursga obuna bo'lgan");
    }

    // ===== cancel =====

    @Test
    void cancel_success_setsCancelledStatus() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.PENDING).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        courseSubscriptionService.cancel(7L);

        assertThat(sub.getStatus()).isEqualTo(CourseSubscriptionStatus.CANCELLED);
        verify(courseSubscriptionRepository).save(sub);
    }

    @Test
    void cancel_notFound_throws() {
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.cancel(7L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ===== expireSubscriptions =====

    @Test
    void expireSubscriptions_marksExpiredAndNotifiesUser() {
        CourseSubscription expiring = CourseSubscription.builder().id(9L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED)
                .endDate(LocalDateTime.now().minusDays(1)).build();

        when(courseSubscriptionRepository.findByStatusAndEndDateBefore(
                eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(List.of(expiring));

        courseSubscriptionService.expireSubscriptions();

        assertThat(expiring.getStatus()).isEqualTo(CourseSubscriptionStatus.EXPIRED);
        verify(courseSubscriptionRepository).save(expiring);
        verify(notificationService).create(eq(student), anyString(), eq("/courses/1"));
    }

    @Test
    void expireSubscriptions_noneExpired_doesNothing() {
        when(courseSubscriptionRepository.findByStatusAndEndDateBefore(
                eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(List.of());

        courseSubscriptionService.expireSubscriptions();

        verify(notificationService, never()).create(any(), anyString(), anyString());
    }

    // ===== confirmOnline (PaymentOrderService.markPaid'dan chaqiriladi) =====

    @Test
    void confirmOnline_newRequest_createsConfirmedSubscription() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.empty());

        CourseSubscriptionDto result =
                courseSubscriptionService.confirmOnline(student, 1L, BigDecimal.valueOf(100_000), 2);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);
        verify(courseSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100000");
        assertThat(Period.between(captor.getValue().getStartDate().toLocalDate(),
                captor.getValue().getEndDate().toLocalDate()).toTotalMonths()).isEqualTo(2);
        verify(notificationService).create(eq(student), anyString(), eq("/courses/1"));
    }

    @Test
    void confirmOnline_existingPendingRequest_confirmsSameRowInsteadOfCreatingNew() {
        CourseSubscription pending = CourseSubscription.builder().id(5L).user(student).course(course)
                .amount(BigDecimal.ZERO).status(CourseSubscriptionStatus.PENDING).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.of(pending));

        CourseSubscriptionDto result =
                courseSubscriptionService.confirmOnline(student, 1L, BigDecimal.valueOf(50_000), 1);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(pending.getStatus()).isEqualTo(CourseSubscriptionStatus.CONFIRMED);
        verify(courseSubscriptionRepository, times(1)).save(pending);
    }

    @Test
    void confirmOnline_courseNotFound_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.confirmOnline(student, 1L, BigDecimal.TEN, 1))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ===== reverseOnline (chargeback/qaytarish) =====

    @Test
    void reverseOnline_confirmedSubscription_cancelsAndNotifiesUser() {
        CourseSubscription confirmed = CourseSubscription.builder().id(88L).user(student).course(course)
                .amount(BigDecimal.valueOf(100_000)).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(88L)).thenReturn(Optional.of(confirmed));

        courseSubscriptionService.reverseOnline(88L);

        assertThat(confirmed.getStatus()).isEqualTo(CourseSubscriptionStatus.CANCELLED);
        verify(courseSubscriptionRepository).save(confirmed);
        verify(notificationService).create(eq(student), anyString(), eq("/courses/1"));
    }

    @Test
    void reverseOnline_nullId_doesNothing() {
        courseSubscriptionService.reverseOnline(null);

        verify(courseSubscriptionRepository, never()).findById(any());
        verify(courseSubscriptionRepository, never()).save(any());
    }

    @Test
    void reverseOnline_alreadyCancelled_isNoOp() {
        CourseSubscription cancelled = CourseSubscription.builder().id(88L).user(student).course(course)
                .amount(BigDecimal.valueOf(100_000)).status(CourseSubscriptionStatus.CANCELLED).build();
        when(courseSubscriptionRepository.findById(88L)).thenReturn(Optional.of(cancelled));

        courseSubscriptionService.reverseOnline(88L);

        verify(courseSubscriptionRepository, never()).save(any());
        verify(notificationService, never()).create(any(), anyString(), anyString());
    }
}
