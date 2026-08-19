package behzoddev.testproject.service.payment;

import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.PaymentSettingsRepository;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.PaymentSettings;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentOrder — Click bilan gaplashishdan oldin/keyin buyurtmaning
 * umumiy hayot aylanishi. markPaid/markCancelled'ning idempotentligi va
 * reversePaidOrder'ning aynan SHU order orqali yaratilgan obunani
 * nishonga olishi (avval shu yerda bug bo'lgan) — asosiy e'tibor shu yerda.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private PaymentSettingsRepository paymentSettingsRepository;
    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private PaymentOrderService paymentOrderService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentOrderService, "pricePerMonthSom", 50_000L);
        Role roleUser = Role.builder().id(1L).roleName("ROLE_USER").build();
        user = User.builder().id(1L).username("student").roles(new HashSet<>(Set.of(roleUser))).build();

        // createOrder() endi minimal summani tekshiradi — bu testlarning
        // aksariyati shu tekshiruvga tegishli emas, shuning uchun lenient.
        PaymentSettings settings = new PaymentSettings();
        settings.setId(1L);
        settings.setMinAmountSom(BigDecimal.valueOf(1000));
        lenient().when(paymentSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
    }

    // ===== createOrder =====

    @Test
    void createOrder_success_computesAmountFromDuration() {
        PaymentOrder order = paymentOrderService.createOrder(user, 3);

        assertThat(order.getAmount()).isEqualByComparingTo("150000");
        assertThat(order.getDurationMonths()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CREATED);
    }

    @Test
    void createOrder_durationOutOfRange_throws() {
        assertThatThrownBy(() -> paymentOrderService.createOrder(user, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paymentOrderService.createOrder(user, 25))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createOrder_amountBelowMinimum_throws() {
        // 1 oy = 50 000 so'm (pricePerMonthSom), minimal chegarani shundan
        // yuqoriroq (60 000) qilib qo'yamiz — buyurtma rad etilishi kerak.
        PaymentSettings settings = new PaymentSettings();
        settings.setId(1L);
        settings.setMinAmountSom(BigDecimal.valueOf(60_000));
        when(paymentSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> paymentOrderService.createOrder(user, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimal chegaradan");
    }

    @Test
    void createOrder_ownerUser_throws() {
        Role roleOwner = Role.builder().id(2L).roleName("ROLE_OWNER").build();
        User owner = User.builder().id(2L).username("owner").roles(new HashSet<>(Set.of(roleOwner))).build();

        assertThatThrownBy(() -> paymentOrderService.createOrder(owner, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OWNER uchun ADMIN obunasi kerak emas");
    }

    // ===== minAmountSom (Click'ning minimal tranzaksiya chegarasi) =====

    @Test
    void updateMinAmountSom_positiveValue_savesAndReturnsIt() {
        when(paymentSettingsRepository.save(any(PaymentSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BigDecimal result = paymentOrderService.updateMinAmountSom(BigDecimal.valueOf(2000));

        assertThat(result).isEqualByComparingTo("2000");
        verify(paymentSettingsRepository).save(any(PaymentSettings.class));
    }

    @Test
    void updateMinAmountSom_nonPositiveValue_throws() {
        assertThatThrownBy(() -> paymentOrderService.updateMinAmountSom(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paymentOrderService.updateMinAmountSom(BigDecimal.valueOf(-500)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paymentOrderService.updateMinAmountSom(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== markPaid =====

    @Test
    void markPaid_success_confirmsOnlineAndLinksSubscriptionId() {
        PaymentOrder order = PaymentOrder.builder().id(10L).user(user).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.CREATED).build();
        when(subscriptionService.confirmOnline(user, order.getAmount(), 1))
                .thenReturn(new SubscriptionDto(77L, 1L, "student", order.getAmount(), "ONLINE", "CONFIRMED",
                        LocalDateTime.now(), LocalDateTime.now().plusMonths(1), null, LocalDateTime.now()));

        paymentOrderService.markPaid(order);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getSubscriptionId()).isEqualTo(77L);
        assertThat(order.getPaidAt()).isNotNull();
    }

    @Test
    void markPaid_alreadyPaid_isIdempotentAndDoesNotConfirmAgain() {
        PaymentOrder order = PaymentOrder.builder().id(10L).user(user).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.PAID).subscriptionId(77L).build();

        paymentOrderService.markPaid(order);

        verify(subscriptionService, never()).confirmOnline(any(), any(), anyInt());
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void markPaid_cancelledOrder_throwsIllegalState() {
        PaymentOrder order = PaymentOrder.builder().id(10L).user(user).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.CANCELLED).build();

        assertThatThrownBy(() -> paymentOrderService.markPaid(order))
                .isInstanceOf(IllegalStateException.class);
        verify(subscriptionService, never()).confirmOnline(any(), any(), anyInt());
    }

    // ===== markCancelled =====

    @Test
    void markCancelled_createdOrder_setsCancelled() {
        PaymentOrder order = PaymentOrder.builder().id(10L).user(user).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.CREATED).build();

        paymentOrderService.markCancelled(order);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
    }

    @Test
    void markCancelled_alreadyPaidOrder_isNoOp() {
        PaymentOrder order = PaymentOrder.builder().id(10L).user(user).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.PAID).build();

        paymentOrderService.markCancelled(order);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
    }

    // ===== reversePaidOrder =====

    @Test
    void reversePaidOrder_delegatesToSubscriptionServiceWithOrdersSubscriptionId() {
        PaymentOrder order = PaymentOrder.builder().id(10L).user(user).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.PAID).subscriptionId(77L).build();

        paymentOrderService.reversePaidOrder(order);

        verify(subscriptionService).reverseOnline(user, 77L);
    }
}
