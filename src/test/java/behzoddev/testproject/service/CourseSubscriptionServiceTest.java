package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.course.CourseSubscriptionDto;
import behzoddev.testproject.dto.course.CreateCourseSubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseSubscription;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
    private User admin;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(
                Role.builder().id(1L).roleName("ROLE_OWNER").build()))).build();
        admin = User.builder().id(50L).username("admin1").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_ADMIN").build()))).build();
        // Muallif — checkCanManage testlari uchun (ROLE_OWNER cheklovsiz,
        // ROLE_ADMIN faqat o'zi yaratgan kursni boshqara oladi).
        course = Course.builder().id(1L).title("Java Asoslari").createdBy(admin).build();
        student = User.builder().id(1L).username("student1").build();
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

    // Foydalanuvchi so'rovi, 2026-09-07: "билдиришномалар фақат шу
    // админнинг ўзига келсин. OWNER учун чеклов йўқ" — ROLE_OWNER'lar
    // baribir cheklovsiz xabar oladi (yuqoridagi test), lekin bundan
    // tashqari kursning muallifi (ROLE_ADMIN bo'lsa ham) HAM alohida xabar
    // olishi kerak — ilgari umuman olmasdi.
    @Test
    void requestSubscription_courseHasAdminAuthor_alsoNotifiesAuthor() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(false);
        when(userRepository.findByRoles_RoleName("ROLE_OWNER")).thenReturn(List.of(owner));

        courseSubscriptionService.requestSubscription(1L, student);

        verify(notificationService).create(eq(owner), anyString(),
                eq("/courses/subscriptions?courseId=1&userId=1"));
        verify(notificationService).create(eq(admin), anyString(),
                eq("/courses/subscriptions?courseId=1&userId=1"));
    }

    // Muallif O'ZI ROLE_OWNER bo'lsa (yuqoridagi tsiklda allaqachon xabar
    // olgan) — ikkinchi marta (takroriy) bildirishnoma yubormasligi kerak.
    @Test
    void requestSubscription_authorIsAlreadyOwner_doesNotNotifyTwice() {
        course.setCreatedBy(owner);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(false);
        when(userRepository.findByRoles_RoleName("ROLE_OWNER")).thenReturn(List.of(owner));

        courseSubscriptionService.requestSubscription(1L, student);

        verify(notificationService, times(1)).create(eq(owner), anyString(), anyString());
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
        // "Manba" — OWNER qo'lda tasdiqladi (foydalanuvchi so'rovi,
        // 2026-09-09: "қайси усулда обуна берилганини қўшиш керак").
        assertThat(result.source()).isEqualTo("MANUAL");
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

    // Kursning muallifi (admin) o'z kursiga obuna berishi/tasdiqlashi
    // mumkin (foydalanuvchi so'rovi, 2026-09-07).
    @Test
    void subscribe_byCourseAuthorAdmin_allowed() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.valueOf(100_000), 1, null);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.empty());

        CourseSubscriptionDto result = courseSubscriptionService.subscribe(1L, dto, admin);

        assertThat(result.status()).isEqualTo("CONFIRMED");
    }

    // Boshqa admin (kurs muallifi emas) obuna berishga urinsa — rad etiladi
    // (foydalanuvchi so'rovi, 2026-09-07: "фақат шу админнинг ўзига").
    @Test
    void subscribe_byUnrelatedAdmin_throwsAccessDenied() {
        CreateCourseSubscriptionDto dto = new CreateCourseSubscriptionDto(1L, BigDecimal.valueOf(100_000), 1, null);
        User otherAdmin = User.builder().id(51L).username("admin2").roles(new HashSet<>(Set.of(
                Role.builder().id(3L).roleName("ROLE_ADMIN").build()))).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseSubscriptionService.subscribe(1L, dto, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(courseSubscriptionRepository, never()).save(any());
    }

    // ===== cancel =====

    @Test
    void cancel_success_setsCancelledStatus() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.PENDING).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        courseSubscriptionService.cancel(7L, owner);

        assertThat(sub.getStatus()).isEqualTo(CourseSubscriptionStatus.CANCELLED);
        verify(courseSubscriptionRepository).save(sub);
    }

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09: "Foydalanuvchini
    // obunasi rad etildi, lekin bildirishnomaga kelmadi USER ga") — PENDING
    // so'rov rad etilganda foydalanuvchiga xabar borishi kerak, aniq "rad
    // etildi" so'zi bilan (CONFIRMED obuna bekor qilinganidan farqli matn).
    @Test
    void cancel_wasPending_notifiesUserWithRejectedWording() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.PENDING).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        courseSubscriptionService.cancel(7L, owner);

        verify(notificationService).create(eq(student), contains("rad etildi"), eq("/courses/1"));
    }

    @Test
    void cancel_wasConfirmed_notifiesUserWithCancelledWording() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        courseSubscriptionService.cancel(7L, owner);

        verify(notificationService).create(eq(student), contains("bekor qilindi"), eq("/courses/1"));
    }

    @Test
    void cancel_notFound_throws() {
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.cancel(7L, owner))
                .isInstanceOf(NoSuchElementException.class);
    }

    // Kursning muallifi (admin) o'z kursining obunasini bekor qila oladi.
    @Test
    void cancel_byCourseAuthorAdmin_allowed() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        courseSubscriptionService.cancel(7L, admin);

        assertThat(sub.getStatus()).isEqualTo(CourseSubscriptionStatus.CANCELLED);
    }

    // Boshqa admin (kurs muallifi emas) bekor qilishga urinsa — rad etiladi.
    @Test
    void cancel_byUnrelatedAdmin_throwsAccessDenied() {
        User otherAdmin = User.builder().id(51L).username("admin2").roles(new HashSet<>(Set.of(
                Role.builder().id(3L).roleName("ROLE_ADMIN").build()))).build();
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> courseSubscriptionService.cancel(7L, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(courseSubscriptionRepository, never()).save(any());
    }

    // ===== updateSubscription ("/courses/subscriptions" — "✏️ Tahrirlash",
    // foydalanuvchi so'rovi, 2026-09-09) =====

    @Test
    void updateSubscription_success_recalculatesEndDateFromStartDate() {
        LocalDateTime start = LocalDateTime.now().minusDays(10);
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED)
                .startDate(start).endDate(start.plusMonths(1)).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        CourseSubscriptionDto result = courseSubscriptionService.updateSubscription(
                7L, BigDecimal.valueOf(200_000), 3, owner);

        assertThat(sub.getAmount()).isEqualByComparingTo("200000");
        assertThat(sub.getEndDate()).isEqualTo(start.plusMonths(3));
        assertThat(sub.getStatus()).isEqualTo(CourseSubscriptionStatus.CONFIRMED);
        assertThat(result.id()).isEqualTo(7L);
        // Tahrirlangandan keyin "Manba" — "qo'lda berilgan" (foydalanuvchi
        // so'rovi, 2026-09-09) — OWNER'ning o'zi bajargan amal.
        assertThat(sub.getConfirmedBy()).isEqualTo(owner);
        assertThat(result.source()).isEqualTo("MANUAL");
        verify(notificationService).create(eq(student), anyString(), eq("/courses/1"));
    }

    // Eskirgan (EXPIRED) yoki adashib bekor qilingan (CANCELLED) obunani
    // tahrirlash uni qayta FAOLlashtiradi — alohida "qayta tiklash"
    // tugmasi shart emas.
    @Test
    void updateSubscription_expiredSubscription_reactivatesToConfirmed() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.EXPIRED)
                .startDate(LocalDateTime.now().minusMonths(2)).endDate(LocalDateTime.now().minusDays(5)).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        courseSubscriptionService.updateSubscription(7L, BigDecimal.valueOf(50_000), 1, owner);

        assertThat(sub.getStatus()).isEqualTo(CourseSubscriptionStatus.CONFIRMED);
    }

    @Test
    void updateSubscription_notFound_throws() {
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.updateSubscription(7L, BigDecimal.TEN, 1, owner))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateSubscription_unrelatedAdmin_throwsAccessDenied() {
        User otherAdmin = User.builder().id(51L).username("admin2").roles(new HashSet<>(Set.of(
                Role.builder().id(3L).roleName("ROLE_ADMIN").build()))).build();
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> courseSubscriptionService.updateSubscription(7L, BigDecimal.TEN, 1, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(courseSubscriptionRepository, never()).save(any());
    }

    @Test
    void updateSubscription_negativeAmount_throws() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> courseSubscriptionService.updateSubscription(7L, BigDecimal.valueOf(-1), 1, owner))
                .isInstanceOf(IllegalArgumentException.class);

        verify(courseSubscriptionRepository, never()).save(any());
    }

    @Test
    void updateSubscription_invalidDuration_throws() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> courseSubscriptionService.updateSubscription(7L, BigDecimal.TEN, 0, owner))
                .isInstanceOf(IllegalArgumentException.class);

        verify(courseSubscriptionRepository, never()).save(any());
    }

    // ===== delete ("/courses/subscriptions" — "🗑️ O'chirish", foydalanuvchi
    // so'rovi, 2026-09-09: "cancel"dan farqli, yozuvni BUTUNLAY o'chiradi) =====

    @Test
    void delete_success_removesRowWithoutNotifying() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CANCELLED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        courseSubscriptionService.delete(7L, owner);

        verify(courseSubscriptionRepository).delete(sub);
        // Ma'muriy tozalash amali — foydalanuvchining kirish huquqiga
        // ta'sir qilmaydi, shuning uchun xabar yuborilmaydi.
        verify(notificationService, never()).create(any(), anyString(), anyString());
    }

    @Test
    void delete_notFound_throws() {
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseSubscriptionService.delete(7L, owner))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void delete_unrelatedAdmin_throwsAccessDenied() {
        User otherAdmin = User.builder().id(51L).username("admin2").roles(new HashSet<>(Set.of(
                Role.builder().id(3L).roleName("ROLE_ADMIN").build()))).build();
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findById(7L)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> courseSubscriptionService.delete(7L, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(courseSubscriptionRepository, never()).delete(any(CourseSubscription.class));
    }

    // ===== getStats ("/payments" sahifasi uchun, foydalanuvchi so'rovi,
    // 2026-09-09: "/payments ma'lumotlari noto'g'ri" — bu sahifa avval
    // kurs obunalarini umuman hisobga olmasdi) =====

    @Test
    void getStats_onlyCountsConfirmedRevenue() {
        CourseSubscription confirmed1 = CourseSubscription.builder().id(1L).user(student).course(course)
                .amount(BigDecimal.valueOf(50_000)).status(CourseSubscriptionStatus.CONFIRMED)
                .endDate(LocalDateTime.now().plusMonths(1)).createdAt(LocalDateTime.now()).build();
        CourseSubscription confirmed2 = CourseSubscription.builder().id(2L).user(student).course(course)
                .amount(BigDecimal.valueOf(50_000)).status(CourseSubscriptionStatus.CONFIRMED)
                .endDate(LocalDateTime.now().minusDays(1)).createdAt(LocalDateTime.now()).build();
        CourseSubscription cancelled = CourseSubscription.builder().id(3L).user(student).course(course)
                .amount(BigDecimal.valueOf(999_999)).status(CourseSubscriptionStatus.CANCELLED)
                .createdAt(LocalDateTime.now()).build();
        CourseSubscription pending = CourseSubscription.builder().id(4L).user(student).course(course)
                .amount(BigDecimal.ZERO).status(CourseSubscriptionStatus.PENDING)
                .createdAt(LocalDateTime.now()).build();
        when(courseSubscriptionRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(confirmed1, confirmed2, cancelled, pending));

        SubscriptionStatsDto stats = courseSubscriptionService.getStats();

        // Faqat 2 ta CONFIRMED hisoblanadi — CANCELLED (999 999) va
        // PENDING umuman kirmaydi.
        assertThat(stats.totalRevenue()).isEqualByComparingTo("100000");
        assertThat(stats.totalConfirmedCount()).isEqualTo(2);
        assertThat(stats.pendingCount()).isEqualTo(1);
        // Faqat endDate hali kelmagan (confirmed1) "faol" hisoblanadi.
        assertThat(stats.activeSubscribersCount()).isEqualTo(1);
    }

    @Test
    void getStats_noSubscriptions_returnsZeroes() {
        when(courseSubscriptionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        SubscriptionStatsDto stats = courseSubscriptionService.getStats();

        assertThat(stats.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.totalConfirmedCount()).isEqualTo(0);
        assertThat(stats.monthlyBreakdown()).isEmpty();
    }

    // ===== listAll (foydalanuvchi so'rovi, 2026-09-07: ROLE_ADMIN faqat
    // o'zi yaratgan kurslarning obunalarini ko'rishi kerak) =====

    @Test
    void listAll_owner_seesEverything() {
        CourseSubscription sub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sub));

        List<CourseSubscriptionDto> result = courseSubscriptionService.listAll(owner);

        assertThat(result).hasSize(1);
    }

    @Test
    void listAll_admin_filtersToOwnCoursesOnly() {
        Course otherCourse = Course.builder().id(2L).title("Boshqa kurs").createdBy(owner).build();
        CourseSubscription ownSub = CourseSubscription.builder().id(7L).user(student).course(course)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        CourseSubscription otherSub = CourseSubscription.builder().id(8L).user(student).course(otherCourse)
                .amount(BigDecimal.TEN).status(CourseSubscriptionStatus.CONFIRMED).build();
        when(courseSubscriptionRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(ownSub, otherSub));

        List<CourseSubscriptionDto> result = courseSubscriptionService.listAll(admin);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(7L);
    }

    // ===== listForCourse =====

    @Test
    void listForCourse_byUnrelatedAdmin_throwsAccessDenied() {
        User otherAdmin = User.builder().id(51L).username("admin2").roles(new HashSet<>(Set.of(
                Role.builder().id(3L).roleName("ROLE_ADMIN").build()))).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseSubscriptionService.listForCourse(1L, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);
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

    // "🎁 3 kunlik bepul sinov" muddati tugaganda — oddiy obunadan farqli
    // xabar matni ("to'lov qilishingiz mumkin"), foydalanuvchi so'rovi,
    // 2026-09-09: "Бонус кун тугаса автомат ҳабар бериши керак".
    @Test
    void expireSubscriptions_trialExpired_notifiesWithPaymentPrompt() {
        CourseSubscription expiringTrial = CourseSubscription.builder().id(9L).user(student).course(course)
                .amount(BigDecimal.ZERO).status(CourseSubscriptionStatus.CONFIRMED).trial(true)
                .endDate(LocalDateTime.now().minusDays(1)).build();

        when(courseSubscriptionRepository.findByStatusAndEndDateBefore(
                eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(List.of(expiringTrial));

        courseSubscriptionService.expireSubscriptions();

        assertThat(expiringTrial.getStatus()).isEqualTo(CourseSubscriptionStatus.EXPIRED);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(student), messageCaptor.capture(), eq("/courses/1"));
        assertThat(messageCaptor.getValue()).contains("BEPUL sinovingiz tugadi").contains("to'lov qilishingiz mumkin");
    }

    @Test
    void expireSubscriptions_noneExpired_doesNothing() {
        when(courseSubscriptionRepository.findByStatusAndEndDateBefore(
                eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(List.of());

        courseSubscriptionService.expireSubscriptions();

        verify(notificationService, never()).create(any(), anyString(), anyString());
    }

    // ===== startFreeTrial =====

    @Test
    void startFreeTrial_eligibleUser_createsConfirmedTrialSubscriptionAndNotifies() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndTrialTrue(1L, 1L)).thenReturn(false);

        CourseSubscriptionDto result = courseSubscriptionService.startFreeTrial(1L, student);

        ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);
        verify(courseSubscriptionRepository).save(captor.capture());
        CourseSubscription saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CourseSubscriptionStatus.CONFIRMED);
        assertThat(saved.isTrial()).isTrue();
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getEndDate()).isCloseTo(LocalDateTime.now().plusDays(3), within(1, java.time.temporal.ChronoUnit.MINUTES));
        assertThat(result).isNotNull();
        // "Manba" — bepul sinov (foydalanuvchi so'rovi, 2026-09-09).
        assertThat(result.source()).isEqualTo("TRIAL");

        verify(notificationService).create(eq(student), anyString(), eq("/courses/1"));
    }

    @Test
    void startFreeTrial_courseIsFree_throws() {
        Course freeCourse = Course.builder().id(2L).title("Bepul kurs").free(true).createdBy(admin).build();
        when(courseRepository.findById(2L)).thenReturn(Optional.of(freeCourse));

        assertThatThrownBy(() -> courseSubscriptionService.startFreeTrial(2L, student))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bepul");

        verify(courseSubscriptionRepository, never()).save(any());
    }

    @Test
    void startFreeTrial_alreadySubscribed_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);

        assertThatThrownBy(() -> courseSubscriptionService.startFreeTrial(1L, student))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon shu kursga obuna");

        verify(courseSubscriptionRepository, never()).save(any());
    }

    @Test
    void startFreeTrial_alreadyUsedTrial_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndTrialTrue(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> courseSubscriptionService.startFreeTrial(1L, student))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon ishlatilgan");

        verify(courseSubscriptionRepository, never()).save(any());
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
        // "Manba" — Click orqali avtomatik, inson ishtirokisiz
        // (foydalanuvchi so'rovi, 2026-09-09: "автоматик (клик орқали
        // тўлов орқали)ми ёки қўлдами?").
        assertThat(result.source()).isEqualTo("ONLINE");
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

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09): sinov
    // FAOL paytida to'lansa, yangi qator yaratish o'rniga O'SHA sinov
    // qatorining o'zi to'lovga "yangilanishi" kerak — aks holda ikkita
    // CONFIRMED qator qolib, eski sinov qatori keyinroq muddati tugab
    // "EXPIRED" bo'lganda, allaqachon to'lagan foydalanuvchiga
    // chalg'ituvchi "sinov tugadi" xabari yuborilardi.
    @Test
    void confirmOnline_activeTrialExists_upgradesSameRowInsteadOfCreatingNew() {
        CourseSubscription activeTrial = CourseSubscription.builder().id(9L).user(student).course(course)
                .amount(BigDecimal.ZERO).status(CourseSubscriptionStatus.CONFIRMED).trial(true)
                .endDate(LocalDateTime.now().plusDays(2)).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.empty());
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.CONFIRMED)).thenReturn(Optional.of(activeTrial));

        CourseSubscriptionDto result =
                courseSubscriptionService.confirmOnline(student, 1L, BigDecimal.valueOf(50_000), 1);

        assertThat(result.id()).isEqualTo(9L);
        assertThat(activeTrial.isTrial()).isFalse();
        assertThat(activeTrial.getAmount()).isEqualByComparingTo("50000");
        verify(courseSubscriptionRepository, times(1)).save(activeTrial);
    }

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09: "3 кун
    // триал берилди... тўлаган суммасига кўра берилган муддатга бонус
    // кунлари ҳам қўшиб берилсин") — sinov FAOL paytida to'lansa, undan
    // qolgan kunlar to'langan muddatga QO'SHILISHI kerak, yo'qolib
    // ketmasligi kerak.
    @Test
    void confirmOnline_activeTrialWithDaysLeft_addsRemainingDaysAsBonus() {
        // "beforeCall" chaqiruvdan OLDIN olinadi — servisning ICHKI "now"i
        // doim shundan KEYIN bo'ladi, shuning uchun quyidagi taqqoslash
        // hech qanday millisekund farqiga qaramay barqaror ishlaydi
        // (avvalgi versiya "LocalDateTime.now()"ni chaqiruvdan KEYIN,
        // xuddi shu +1 kun siljish bilan solishtirardi — bu ikkalasi bir
        // xil formula bo'lgani uchun deyarli har doim muvaffaqiyatsiz
        // tugagan, haqiqiy topilgan flaky test).
        LocalDateTime beforeCall = LocalDateTime.now();
        CourseSubscription activeTrial = CourseSubscription.builder().id(9L).user(student).course(course)
                .amount(BigDecimal.ZERO).status(CourseSubscriptionStatus.CONFIRMED).trial(true)
                .endDate(beforeCall.plusDays(2)).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.empty());
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.CONFIRMED)).thenReturn(Optional.of(activeTrial));

        courseSubscriptionService.confirmOnline(student, 1L, BigDecimal.valueOf(50_000), 1);

        // 1 oy + kamida 1 kun bonus (sinovdan ~2 kun qolgani, lekin
        // Duration#toDays() pastga yaxlitlashi mumkinligi hisobga olingan).
        assertThat(activeTrial.getEndDate()).isAfter(beforeCall.plusMonths(1));
        assertThat(activeTrial.getNote()).contains("bonus");
    }

    @Test
    void confirmOnline_noActiveTrial_noBonusDaysAdded() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.PENDING)).thenReturn(Optional.empty());
        when(courseSubscriptionRepository.findByUser_IdAndCourse_IdAndStatus(
                1L, 1L, CourseSubscriptionStatus.CONFIRMED)).thenReturn(Optional.empty());

        ArgumentCaptor<CourseSubscription> captor = ArgumentCaptor.forClass(CourseSubscription.class);

        courseSubscriptionService.confirmOnline(student, 1L, BigDecimal.valueOf(50_000), 1);

        verify(courseSubscriptionRepository).save(captor.capture());
        assertThat(Period.between(captor.getValue().getStartDate().toLocalDate(),
                captor.getValue().getEndDate().toLocalDate()).toTotalMonths()).isEqualTo(1);
        assertThat(captor.getValue().getNote()).doesNotContain("bonus");
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
