package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.SubscriptionService;
import behzoddev.testproject.service.UserServiceImpl;
import behzoddev.testproject.service.payment.PaymentOrderService;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Botda OWNER uchun: 👑 Foydalanuvchilar, 💰 To'lovlar, ⚙️ Tizim sozlamalari,
 * 📢 E'lon yuborish — haqiqiy UserServiceImpl/SubscriptionService/
 * PaymentOrderService orqali.
 */
@ExtendWith(MockitoExtension.class)
class TelegramOwnerServiceTest {

    private static final Long CHAT_ID = 1000L;

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserServiceImpl userServiceImpl;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentOrderService paymentOrderService;
    @Mock
    private TelegramSessionService sessionService;

    @InjectMocks
    private TelegramOwnerService ownerService;

    private User owner;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).roleName("ROLE_OWNER").build();
        owner = User.builder().id(1L).username("owner").telegramId(CHAT_ID)
                .roles(new HashSet<>(Set.of(role))).build();
    }

    // ===== Foydalanuvchilar =====

    @Test
    void applyUserSearch_notFound_clearsSessionAndReportsError() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        SendMessage msg = ownerService.applyUserSearch(CHAT_ID, "ghost");

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("topilmadi");
    }

    @Test
    void userCard_showsRoleTogglesAndUnlockIfLocked() {
        Role userRole = Role.builder().id(2L).roleName("ROLE_USER").build();
        User target = User.builder().id(5L).username("student1")
                .roles(new HashSet<>(Set.of(userRole))).build();
        // isAccountNonLocked() haqiqiy User.lockedUntil'ga bog'liq — bloklanmagan (default holat).
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        SendMessage msg = ownerService.userCard(CHAT_ID, 5L);

        assertThat(msg.getText()).contains("student1");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void toggleRole_add_callsUserServiceAndRefreshesCard() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(owner));
        Role userRole = Role.builder().id(2L).roleName("ROLE_USER").build();
        User target = User.builder().id(5L).username("student1")
                .roles(new HashSet<>(Set.of(userRole))).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        ownerService.toggleRole(CHAT_ID, 5L, "ROLE_ADMIN", true);

        verify(userServiceImpl).addRole(eq(5L), eq("ROLE_ADMIN"), any(Authentication.class));
    }

    @Test
    void toggleRole_selfChangeRejected_showsErrorWithoutCrashing() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(owner));
        doThrow(new RuntimeException("⛔ Siz o'z rolingizni o'zgartira olmaysiz."))
                .when(userServiceImpl).addRole(anyLong(), any(), any());

        SendMessage msg = ownerService.toggleRole(CHAT_ID, 1L, "ROLE_ADMIN", true);

        assertThat(msg.getText()).contains("❌");
    }

    @Test
    void unlockUser_callsServiceAndRefreshesCard() {
        Role userRole = Role.builder().id(2L).roleName("ROLE_USER").build();
        User target = User.builder().id(5L).username("student1")
                .roles(new HashSet<>(Set.of(userRole))).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        ownerService.unlockUser(CHAT_ID, 5L);

        verify(userServiceImpl).unlockUser(5L);
    }

    // ===== To'lovlar =====

    @Test
    void listPendingPayments_none_showsStatsOnly() {
        when(subscriptionService.listPending()).thenReturn(List.of());
        when(subscriptionService.getStats()).thenReturn(SubscriptionStatsDto.builder()
                .totalRevenue(BigDecimal.valueOf(500_000)).thisMonthRevenue(BigDecimal.valueOf(100_000))
                .totalConfirmedCount(10).activeSubscribersCount(3).pendingCount(0)
                .monthlyBreakdown(List.of()).build());

        SendMessage msg = ownerService.listPendingPayments(owner);

        assertThat(msg.getText()).contains("kutilayotgan so'rov yo'q");
    }

    @Test
    void listPendingPayments_hasPending_listsButtons() {
        when(subscriptionService.listPending()).thenReturn(List.of(
                new SubscriptionDto(1L, 5L, "student1", BigDecimal.valueOf(50_000), "TELEGRAM", "PENDING",
                        null, null, null, LocalDateTime.now())
        ));
        when(subscriptionService.getStats()).thenReturn(SubscriptionStatsDto.builder()
                .totalRevenue(BigDecimal.ZERO).thisMonthRevenue(BigDecimal.ZERO)
                .totalConfirmedCount(0).activeSubscribersCount(0).pendingCount(1)
                .monthlyBreakdown(List.of()).build());

        SendMessage msg = ownerService.listPendingPayments(owner);

        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void showPaymentDetail_alreadyHandled_saysSo() {
        when(subscriptionService.listPending()).thenReturn(List.of());

        SendMessage msg = ownerService.showPaymentDetail(CHAT_ID, 999L);

        assertThat(msg.getText()).contains("allaqachon ko'rib chiqilgan");
    }

    @Test
    void confirmPayment_success_grantsAdmin() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(owner));

        SendMessage msg = ownerService.confirmPayment(CHAT_ID, 1L);

        verify(subscriptionService).confirm(1L, null, owner);
        assertThat(msg.getText()).contains("✅");
    }

    @Test
    void rejectPayment_success_cancels() {
        SendMessage msg = ownerService.rejectPayment(CHAT_ID, 1L);

        verify(subscriptionService).cancel(1L);
        assertThat(msg.getText()).contains("rad etildi");
    }

    // ===== Tizim sozlamalari =====

    @Test
    void showSettings_displaysCurrentMinAmount() {
        when(paymentOrderService.getMinAmountSom()).thenReturn(BigDecimal.valueOf(1000));

        SendMessage msg = ownerService.showSettings(CHAT_ID);

        assertThat(msg.getText()).contains("1 000 so'm");
    }

    @Test
    void applyMinAmount_valid_updatesAndClears() {
        SendMessage msg = ownerService.applyMinAmount(CHAT_ID, "2000");

        verify(paymentOrderService).updateMinAmountSom(BigDecimal.valueOf(2000));
        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("✅");
    }

    @Test
    void applyMinAmount_notANumber_retriesWithoutClearing() {
        SendMessage msg = ownerService.applyMinAmount(CHAT_ID, "abc");

        verify(sessionService, never()).clear(CHAT_ID);
        assertThat(msg.getText()).contains("Faqat raqam");
    }

    // ===== E'lon yuborish =====

    @Test
    void previewBroadcast_storesTextAndShowsRecipientCount() {
        User u1 = User.builder().id(2L).telegramId(11L).build();
        User u2 = User.builder().id(3L).telegramId(null).build(); // ulanmagan — hisobga olinmaydi
        when(userRepository.findAll()).thenReturn(List.of(owner, u1, u2));

        SendMessage msg = ownerService.previewBroadcast(CHAT_ID, "Diqqat, muhim xabar!");

        verify(sessionService).putTempData(CHAT_ID, "tg_broadcastText", "Diqqat, muhim xabar!");
        assertThat(msg.getText()).contains("2 ta").contains("Diqqat, muhim xabar!");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void buildBroadcastMessages_buildsOnePerRecipientPlusConfirmation() {
        User u1 = User.builder().id(2L).telegramId(11L).build();
        User u2 = User.builder().id(3L).telegramId(22L).build();
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("tg_broadcastText", "Salom hammaga!"));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        List<SendMessage> messages = ownerService.buildBroadcastMessages(CHAT_ID);

        verify(sessionService).clear(CHAT_ID);
        assertThat(messages).hasSize(3); // 2 qabul qiluvchi + OWNER'ga tasdiq
        assertThat(messages.get(0).getText()).contains("Salom hammaga!");
        assertThat(messages.get(1).getText()).contains("Salom hammaga!");
        assertThat(messages.get(2).getText()).contains("2 ta");
    }

    @Test
    void cancelBroadcast_clearsSession() {
        SendMessage msg = ownerService.cancelBroadcast(CHAT_ID);

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("bekor qilindi");
    }
}
