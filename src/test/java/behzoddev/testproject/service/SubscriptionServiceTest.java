package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.SubscriptionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.subscription.CreateSubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.Subscription;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.RoleAuditAction;
import behzoddev.testproject.entity.enums.RoleAuditSource;
import behzoddev.testproject.entity.enums.SubscriptionSource;
import behzoddev.testproject.entity.enums.SubscriptionStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ROLE_ADMIN'ni obuna orqali berish/olib tashlash logikasi — ayniqsa
 * reverseOnline/expireSubscriptions'dagi "boshqa faol obuna bormi" tekshiruvi
 * (aynan shu joyda avval haqiqiy bug bo'lgan edi, bu testlar o'shani ushlab turadi).
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RoleAuditService roleAuditService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Role roleUser;
    private Role roleAdmin;
    private User owner;

    @BeforeEach
    void setUp() {
        roleUser = Role.builder().id(1L).roleName("ROLE_USER").build();
        roleAdmin = Role.builder().id(2L).roleName("ROLE_ADMIN").build();
        owner = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(roleAdmin))).build();
    }

    private User userWithRoles(Long id, Role... roles) {
        return User.builder().id(id).username("user" + id).roles(new HashSet<>(Set.of(roles))).build();
    }

    // ===== createManual =====

    @Test
    void createManual_success_grantsAdminAndNotifies() {
        User user = userWithRoles(1L, roleUser);
        CreateSubscriptionDto dto = new CreateSubscriptionDto(1L, BigDecimal.valueOf(100_000), 3, "izoh");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        SubscriptionDto result = subscriptionService.createManual(dto, owner);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.source()).isEqualTo("MANUAL");
        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100000");
        assertThat(Period.between(
                captor.getValue().getStartDate().toLocalDate(),
                captor.getValue().getEndDate().toLocalDate()).toTotalMonths()).isEqualTo(3);

        verify(roleAuditService).record(user, owner, "ROLE_ADMIN", RoleAuditAction.GRANTED, RoleAuditSource.SUBSCRIPTION);
        verify(notificationService).create(eq(user), anyString(), eq("/profile"));
    }

    @Test
    void createManual_nullDuration_defaultsToOneMonth() {
        User user = userWithRoles(1L, roleUser);
        CreateSubscriptionDto dto = new CreateSubscriptionDto(1L, BigDecimal.valueOf(50_000), null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        subscriptionService.createManual(dto, owner);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(Period.between(
                captor.getValue().getStartDate().toLocalDate(),
                captor.getValue().getEndDate().toLocalDate()).toTotalMonths()).isEqualTo(1);
    }

    @Test
    void createManual_userNotFound_throws() {
        CreateSubscriptionDto dto = new CreateSubscriptionDto(1L, BigDecimal.TEN, 1, null);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.createManual(dto, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Foydalanuvchi topilmadi");
    }

    @Test
    void createManual_invalidAmount_throws() {
        User user = userWithRoles(1L, roleUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CreateSubscriptionDto zero = new CreateSubscriptionDto(1L, BigDecimal.ZERO, 1, null);
        assertThatThrownBy(() -> subscriptionService.createManual(zero, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("To'lov summasi noto'g'ri");

        CreateSubscriptionDto nullAmount = new CreateSubscriptionDto(1L, null, 1, null);
        assertThatThrownBy(() -> subscriptionService.createManual(nullAmount, owner))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createManual_userAlreadyAdmin_doesNotRegrant() {
        User user = userWithRoles(1L, roleUser, roleAdmin);
        CreateSubscriptionDto dto = new CreateSubscriptionDto(1L, BigDecimal.valueOf(10_000), 1, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        subscriptionService.createManual(dto, owner);

        verify(roleRepository, never()).findByRoleName(anyString());
        verify(roleAuditService, never()).record(any(), any(), any(), any(), any());
    }

    // ===== confirm =====

    @Test
    void confirm_success() {
        User user = userWithRoles(1L, roleUser);
        Subscription pending = Subscription.builder().id(5L).user(user).amount(BigDecimal.TEN)
                .source(SubscriptionSource.TELEGRAM).status(SubscriptionStatus.PENDING).build();

        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        SubscriptionDto result = subscriptionService.confirm(5L, 2, owner);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();
        verify(notificationService).create(eq(user), anyString(), eq("/profile"));
    }

    @Test
    void confirm_notFound_throws() {
        when(subscriptionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.confirm(5L, 1, owner))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("So'rov topilmadi");
    }

    @Test
    void confirm_alreadyProcessed_throws() {
        Subscription confirmed = Subscription.builder().id(5L).user(owner).amount(BigDecimal.TEN)
                .source(SubscriptionSource.MANUAL).status(SubscriptionStatus.CONFIRMED).build();
        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> subscriptionService.confirm(5L, 1, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon ko'rib chiqilgan");
    }

    // ===== cancel =====

    @Test
    void cancel_success() {
        Subscription pending = Subscription.builder().id(7L).user(owner).amount(BigDecimal.TEN)
                .source(SubscriptionSource.TELEGRAM).status(SubscriptionStatus.PENDING).build();
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(pending));

        SubscriptionDto result = subscriptionService.cancel(7L);

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_notFound_throws() {
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancel(7L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void cancel_notPending_throws() {
        Subscription confirmed = Subscription.builder().id(7L).user(owner).amount(BigDecimal.TEN)
                .source(SubscriptionSource.MANUAL).status(SubscriptionStatus.CONFIRMED).build();
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> subscriptionService.cancel(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Faqat kutilayotgan so'rovni bekor qilish mumkin");
    }

    // ===== confirmOnline =====

    @Test
    void confirmOnline_success_ownerIsNullSinceAutomatic() {
        User user = userWithRoles(1L, roleUser);
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        SubscriptionDto result = subscriptionService.confirmOnline(user, BigDecimal.valueOf(200_000), 6);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.source()).isEqualTo("ONLINE");
        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();
        verify(roleAuditService).record(user, null, "ROLE_ADMIN", RoleAuditAction.GRANTED, RoleAuditSource.SUBSCRIPTION);
    }

    // ===== reverseOnline (bu yerda avval haqiqiy bug bo'lgan) =====

    @Test
    void reverseOnline_noOtherActiveSubscription_cancelsAndRevokesAdmin() {
        User user = userWithRoles(1L, roleUser, roleAdmin);
        Subscription confirmed = Subscription.builder().id(10L).user(user).amount(BigDecimal.TEN)
                .source(SubscriptionSource.ONLINE).status(SubscriptionStatus.CONFIRMED).build();

        when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(confirmed));
        when(subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                eq(1L), eq(SubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        subscriptionService.reverseOnline(user, 10L);

        assertThat(confirmed.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(user.hasRole("ROLE_ADMIN")).isFalse();
        verify(roleAuditService).record(user, null, "ROLE_ADMIN", RoleAuditAction.REVOKED, RoleAuditSource.SYSTEM);
        verify(notificationService).create(eq(user), anyString(), eq("/profile"));
    }

    @Test
    void reverseOnline_hasOtherActiveSubscription_keepsAdmin() {
        User user = userWithRoles(1L, roleUser, roleAdmin);
        Subscription confirmed = Subscription.builder().id(10L).user(user).amount(BigDecimal.TEN)
                .source(SubscriptionSource.ONLINE).status(SubscriptionStatus.CONFIRMED).build();

        when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(confirmed));
        when(subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                eq(1L), eq(SubscriptionStatus.CONFIRMED), any())).thenReturn(true);

        subscriptionService.reverseOnline(user, 10L);

        assertThat(confirmed.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();
        verify(roleAuditService, never()).record(any(), any(), any(), any(), any());
        verify(notificationService, never()).create(any(), anyString(), anyString());
    }

    @Test
    void reverseOnline_userHasOnlyAdminRole_safetyGuardKeepsRoleButStillNotifies() {
        // revokeAdmin() ichidagi xavfsizlik tekshiruvi: kamida bitta rol qolishi
        // shart, shuning uchun yagona rol ADMIN bo'lsa ham u olib tashlanmaydi —
        // lekin bildirishnoma baribir yuboriladi (reverseOnline shartsiz jo'natadi).
        User user = userWithRoles(1L, roleAdmin);
        when(subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                eq(1L), eq(SubscriptionStatus.CONFIRMED), any())).thenReturn(false);

        subscriptionService.reverseOnline(user, null);

        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();
        verify(roleRepository, never()).findByRoleName(anyString());
        verify(notificationService).create(eq(user), anyString(), eq("/profile"));
    }

    // ===== expireSubscriptions =====

    @Test
    void expireSubscriptions_noOtherActive_expiresAndRevokesAdmin() {
        User user = userWithRoles(1L, roleUser, roleAdmin);
        Subscription expiring = Subscription.builder().id(20L).user(user).amount(BigDecimal.TEN)
                .source(SubscriptionSource.MANUAL).status(SubscriptionStatus.CONFIRMED)
                .endDate(LocalDateTime.now().minusDays(1)).build();

        when(subscriptionRepository.findByStatusAndEndDateBefore(eq(SubscriptionStatus.CONFIRMED), any()))
                .thenReturn(List.of(expiring));
        when(subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                eq(1L), eq(SubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        subscriptionService.expireSubscriptions();

        assertThat(expiring.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(user.hasRole("ROLE_ADMIN")).isFalse();
        verify(notificationService).create(eq(user), anyString(), anyString());
    }

    @Test
    void expireSubscriptions_hasOtherActive_keepsAdmin() {
        User user = userWithRoles(1L, roleUser, roleAdmin);
        Subscription expiring = Subscription.builder().id(20L).user(user).amount(BigDecimal.TEN)
                .source(SubscriptionSource.MANUAL).status(SubscriptionStatus.CONFIRMED)
                .endDate(LocalDateTime.now().minusDays(1)).build();

        when(subscriptionRepository.findByStatusAndEndDateBefore(eq(SubscriptionStatus.CONFIRMED), any()))
                .thenReturn(List.of(expiring));
        when(subscriptionRepository.existsByUser_IdAndStatusAndEndDateAfter(
                eq(1L), eq(SubscriptionStatus.CONFIRMED), any())).thenReturn(true);

        subscriptionService.expireSubscriptions();

        assertThat(expiring.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();
        verify(notificationService, never()).create(any(), anyString(), anyString());
        verify(roleAuditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void expireSubscriptions_noExpiredFound_doesNothing() {
        when(subscriptionRepository.findByStatusAndEndDateBefore(eq(SubscriptionStatus.CONFIRMED), any()))
                .thenReturn(List.of());

        subscriptionService.expireSubscriptions();

        verify(subscriptionRepository, times(0)).existsByUser_IdAndStatusAndEndDateAfter(any(), any(), any());
        verify(notificationService, never()).create(any(), anyString(), anyString());
    }
}
