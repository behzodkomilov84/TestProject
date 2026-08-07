package behzoddev.testproject.service.payment;

import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.PaymentTransactionRepository;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.PaymentTransaction;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.entity.enums.PaymentProvider;
import behzoddev.testproject.entity.enums.PaymentTransactionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Click Merchant API — bitta webhook, {@code action} bo'yicha Prepare/Complete.
 * Imzo tekshiruvi (MD5) va idempotentlik (bir xil click_trans_id qayta
 * kelsa ham tranzaksiya ikki marta yaratilmasligi/yakunlanmasligi) — bu
 * yerda avval qo'lda (synthetic so'rovlar bilan) tekshirilgan, endi
 * avtomatik regressiya sifatida qulflab qo'yiladi.
 */
@ExtendWith(MockitoExtension.class)
class ClickServiceTest {

    private static final String SERVICE_ID = "SVC1";
    private static final String MERCHANT_ID = "MERCH1";
    private static final String SECRET_KEY = "SECRET";

    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentOrderService paymentOrderService;

    @InjectMocks
    private ClickService clickService;

    private PaymentOrder order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clickService, "serviceId", SERVICE_ID);
        ReflectionTestUtils.setField(clickService, "merchantId", MERCHANT_ID);
        ReflectionTestUtils.setField(clickService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(clickService, "payBaseUrl", "https://my.click.uz/services/pay");

        User user = User.builder().id(1L).username("student").build();
        order = PaymentOrder.builder().id(5L).user(user).amount(new BigDecimal("50000.00"))
                .durationMonths(1).status(PaymentOrderStatus.CREATED).build();
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> prepareParams(String clickTransId, String merchantTransId, String amount, boolean validSign) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("click_trans_id", clickTransId);
        params.put("merchant_trans_id", merchantTransId);
        params.put("amount", amount);
        params.put("action", "0");
        params.put("sign_time", "2026-08-08 00:00:00");

        String raw = clickTransId + SERVICE_ID + SECRET_KEY + merchantTransId + amount + "0" + params.get("sign_time");
        params.put("sign_string", validSign ? md5(raw) : "wrong");
        return params;
    }

    private Map<String, String> completeParams(String clickTransId, String merchantTransId, String amount,
                                                String merchantPrepareId, String error, boolean validSign) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("click_trans_id", clickTransId);
        params.put("merchant_trans_id", merchantTransId);
        params.put("merchant_prepare_id", merchantPrepareId);
        params.put("amount", amount);
        params.put("action", "1");
        params.put("sign_time", "2026-08-08 00:00:00");
        params.put("error", error);

        String raw = clickTransId + SERVICE_ID + SECRET_KEY + merchantTransId + merchantPrepareId + amount + "1" + params.get("sign_time");
        params.put("sign_string", validSign ? md5(raw) : "wrong");
        return params;
    }

    // ===== signature tekshiruvi =====

    @Test
    void handle_invalidSignature_returnsErrorWithoutTouchingRepositories() {
        Map<String, String> params = prepareParams("100", "5", "50000.00", false);

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(-1);
        assertThat(result.get("error_note")).isEqualTo("SIGN CHECK FAILED!");
        verify(paymentOrderRepository, never()).findById(any());
    }

    // ===== prepare (action=0) =====

    @Test
    void prepare_success_createsPendingTransaction() {
        Map<String, String> params = prepareParams("100", "5", "50000.00", true);
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.CLICK, "100"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(1L);
            return tx;
        });

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(0);
        assertThat(result.get("merchant_prepare_id")).isEqualTo(1L);
    }

    @Test
    void prepare_orderAlreadyPaid_returnsErrorMinus4() {
        order.setStatus(PaymentOrderStatus.PAID);
        Map<String, String> params = prepareParams("100", "5", "50000.00", true);
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(-4);
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void prepare_wrongAmount_returnsErrorMinus2() {
        Map<String, String> params = prepareParams("100", "5", "1.00", true);
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(-2);
    }

    @Test
    void prepare_orderNotFound_returnsErrorMinus5() {
        Map<String, String> params = prepareParams("100", "999", "50000.00", true);
        when(paymentOrderRepository.findById(999L)).thenReturn(Optional.empty());

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(-5);
    }

    // ===== complete (action=1) =====

    @Test
    void complete_success_marksOrderPaid() {
        PaymentTransaction tx = PaymentTransaction.builder().id(1L).order(order).provider(PaymentProvider.CLICK)
                .providerTransactionId("100").amount(order.getAmount()).state(PaymentTransactionState.CREATED)
                .createTime(LocalDateTime.now()).build();

        Map<String, String> params = completeParams("100", "5", "50000.00", "1", "0", true);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.CLICK, "100"))
                .thenReturn(Optional.of(tx));

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(0);
        assertThat(tx.getState()).isEqualTo(PaymentTransactionState.PERFORMED);
        verify(paymentOrderService).markPaid(order);
    }

    @Test
    void complete_incomingErrorNegative_cancelsInsteadOfPaying() {
        PaymentTransaction tx = PaymentTransaction.builder().id(1L).order(order).provider(PaymentProvider.CLICK)
                .providerTransactionId("100").amount(order.getAmount()).state(PaymentTransactionState.CREATED)
                .createTime(LocalDateTime.now()).build();

        Map<String, String> params = completeParams("100", "5", "50000.00", "1", "-5", true);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.CLICK, "100"))
                .thenReturn(Optional.of(tx));

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(0);
        assertThat(tx.getState()).isEqualTo(PaymentTransactionState.CANCELLED);
        verify(paymentOrderService).markCancelled(order);
        verify(paymentOrderService, never()).markPaid(any());
    }

    @Test
    void complete_alreadyCancelledTransaction_returnsErrorMinus9() {
        PaymentTransaction tx = PaymentTransaction.builder().id(1L).order(order).provider(PaymentProvider.CLICK)
                .providerTransactionId("100").amount(order.getAmount()).state(PaymentTransactionState.CANCELLED)
                .createTime(LocalDateTime.now()).cancelTime(LocalDateTime.now()).build();

        Map<String, String> params = completeParams("100", "5", "50000.00", "1", "0", true);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.CLICK, "100"))
                .thenReturn(Optional.of(tx));

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(-9);
        verify(paymentOrderService, never()).markPaid(any());
    }

    @Test
    void complete_alreadyPerformed_isIdempotentAndDoesNotMarkPaidTwice() {
        PaymentTransaction tx = PaymentTransaction.builder().id(1L).order(order).provider(PaymentProvider.CLICK)
                .providerTransactionId("100").amount(order.getAmount()).state(PaymentTransactionState.PERFORMED)
                .createTime(LocalDateTime.now()).performTime(LocalDateTime.now()).build();

        Map<String, String> params = completeParams("100", "5", "50000.00", "1", "0", true);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.CLICK, "100"))
                .thenReturn(Optional.of(tx));

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(0);
        verify(paymentOrderService, never()).markPaid(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void complete_transactionNotFound_returnsErrorMinus6() {
        Map<String, String> params = completeParams("999", "5", "50000.00", "1", "0", true);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.CLICK, "999"))
                .thenReturn(Optional.empty());

        Map<String, Object> result = clickService.handle(params);

        assertThat(result.get("error")).isEqualTo(-6);
    }

    // ===== buildPayUrl =====

    @Test
    void buildPayUrl_containsAllRequiredParams() {
        String url = clickService.buildPayUrl(order, "https://example.com/return");

        assertThat(url).startsWith("https://my.click.uz/services/pay?");
        assertThat(url).contains("service_id=" + SERVICE_ID);
        assertThat(url).contains("merchant_id=" + MERCHANT_ID);
        assertThat(url).contains("transaction_param=5");
    }

    @Test
    void isEnabled_trueOnlyWhenAllCredentialsPresent() {
        assertThat(clickService.isEnabled()).isTrue();

        ReflectionTestUtils.setField(clickService, "secretKey", "");
        assertThat(clickService.isEnabled()).isFalse();
    }
}
