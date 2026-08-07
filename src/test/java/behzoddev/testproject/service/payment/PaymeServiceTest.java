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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Payme (Paycom) Merchant API — JSON-RPC 2.0. Idempotentlik
 * (CreateTransaction/PerformTransaction bir xil so'rov bilan qayta kelsa)
 * va chargeback (CancelTransaction Perform'dan KEYIN kelsa reverseOnline
 * chaqirilishi, OLDIN kelsa oddiy markCancelled) — avval qo'lda (synthetic
 * so'rovlar bilan) tekshirilgan, endi avtomatik regressiya sifatida qulflab
 * qo'yiladi.
 */
@ExtendWith(MockitoExtension.class)
class PaymeServiceTest {

    private static final String MERCHANT_ID = "MERCH1";
    private static final String KEY = "SECRETKEY";

    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentOrderService paymentOrderService;

    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymeService paymeService;

    private PaymentOrder order;
    private String validAuthHeader;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        ReflectionTestUtils.setField(paymeService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(paymeService, "merchantId", MERCHANT_ID);
        ReflectionTestUtils.setField(paymeService, "key", KEY);
        ReflectionTestUtils.setField(paymeService, "checkoutBaseUrl", "https://checkout.paycom.uz");

        User user = User.builder().id(1L).username("student").build();
        order = PaymentOrder.builder().id(5L).user(user).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.CREATED).build();

        validAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(("Paycom:" + KEY).getBytes(StandardCharsets.UTF_8));
    }

    private ObjectNode request(String method, ObjectNode params) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("id", 1);
        req.put("method", method);
        req.set("params", params);
        return req;
    }

    private ObjectNode accountParams(long amountTiyin, Long orderId) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("amount", amountTiyin);
        params.putObject("account").put("order_id", orderId == null ? "" : orderId.toString());
        return params;
    }

    private ObjectNode idParams(String paymeTxId) {
        return objectMapper.createObjectNode().put("id", paymeTxId);
    }

    private static final long AMOUNT_TIYIN = 5_000_000L; // 50 000 so'm

    // ===== auth =====

    @Test
    void handle_missingAuthHeader_returnsInsufficientPrivilegeError() {
        JsonNode req = request("CheckPerformTransaction", accountParams(AMOUNT_TIYIN, 5L));

        ObjectNode response = paymeService.handle(req, null);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32504);
        verify(paymentOrderRepository, never()).findById(any());
    }

    @Test
    void handle_wrongAuthPassword_returnsInsufficientPrivilegeError() {
        String wrongAuth = "Basic " + Base64.getEncoder()
                .encodeToString("Paycom:WRONG".getBytes(StandardCharsets.UTF_8));
        JsonNode req = request("CheckPerformTransaction", accountParams(AMOUNT_TIYIN, 5L));

        ObjectNode response = paymeService.handle(req, wrongAuth);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32504);
    }

    @Test
    void handle_unknownMethod_returnsMethodNotFound() {
        JsonNode req = request("SomeUnknownMethod", objectMapper.createObjectNode());

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
    }

    // ===== CheckPerformTransaction =====

    @Test
    void checkPerformTransaction_validOrder_allowsTrue() {
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));
        JsonNode req = request("CheckPerformTransaction", accountParams(AMOUNT_TIYIN, 5L));

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("result").get("allow").asBoolean()).isTrue();
    }

    @Test
    void checkPerformTransaction_orderNotFound_returnsMinus31050() {
        when(paymentOrderRepository.findById(999L)).thenReturn(Optional.empty());
        JsonNode req = request("CheckPerformTransaction", accountParams(AMOUNT_TIYIN, 999L));

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-31050);
    }

    @Test
    void checkPerformTransaction_wrongAmount_returnsMinus31001() {
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));
        JsonNode req = request("CheckPerformTransaction", accountParams(1L, 5L));

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-31001);
    }

    // ===== CreateTransaction =====

    @Test
    void createTransaction_success_createsTransactionInStateOne() {
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.empty());
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findByOrder_IdAndStateNot(5L, PaymentTransactionState.CANCELLED))
                .thenReturn(java.util.List.of());
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(10L);
            return tx;
        });

        ObjectNode params = accountParams(AMOUNT_TIYIN, 5L);
        params.put("id", "tx1");
        JsonNode req = request("CreateTransaction", params);

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("result").get("state").asInt()).isEqualTo(1);
        assertThat(response.get("result").get("transaction").asText()).isEqualTo("10");
    }

    @Test
    void createTransaction_idempotent_existingTransactionReturnsSameResultWithoutCreatingNew() {
        PaymentTransaction existing = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.CREATED)
                .createTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(existing));

        ObjectNode params = accountParams(AMOUNT_TIYIN, 5L);
        params.put("id", "tx1");
        JsonNode req = request("CreateTransaction", params);

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("result").get("transaction").asText()).isEqualTo("10");
        assertThat(response.get("result").get("state").asInt()).isEqualTo(1);
        verify(paymentTransactionRepository, never()).save(any());
        verify(paymentOrderRepository, never()).findById(any());
    }

    @Test
    void createTransaction_existingButCancelled_returnsMinus31008() {
        PaymentTransaction cancelled = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.CANCELLED)
                .createTime(LocalDateTime.now()).cancelTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(cancelled));

        ObjectNode params = accountParams(AMOUNT_TIYIN, 5L);
        params.put("id", "tx1");
        JsonNode req = request("CreateTransaction", params);

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-31008);
    }

    @Test
    void createTransaction_orderAlreadyPaid_returnsMinus31050() {
        order.setStatus(PaymentOrderStatus.PAID);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.empty());
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));

        ObjectNode params = accountParams(AMOUNT_TIYIN, 5L);
        params.put("id", "tx1");
        JsonNode req = request("CreateTransaction", params);

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-31050);
    }

    @Test
    void createTransaction_anotherActiveTransactionExists_returnsMinus31050() {
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx2"))
                .thenReturn(Optional.empty());
        when(paymentOrderRepository.findById(5L)).thenReturn(Optional.of(order));
        PaymentTransaction other = PaymentTransaction.builder().id(1L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.CREATED).build();
        when(paymentTransactionRepository.findByOrder_IdAndStateNot(5L, PaymentTransactionState.CANCELLED))
                .thenReturn(java.util.List.of(other));

        ObjectNode params = accountParams(AMOUNT_TIYIN, 5L);
        params.put("id", "tx2");
        JsonNode req = request("CreateTransaction", params);

        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-31050);
    }

    // ===== PerformTransaction =====

    @Test
    void performTransaction_success_marksOrderPaid() {
        PaymentTransaction tx = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.CREATED)
                .createTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(tx));

        JsonNode req = request("PerformTransaction", idParams("tx1"));
        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("result").get("state").asInt()).isEqualTo(2);
        assertThat(tx.getState()).isEqualTo(PaymentTransactionState.PERFORMED);
        verify(paymentOrderService).markPaid(order);
    }

    @Test
    void performTransaction_alreadyPerformed_isIdempotentAndDoesNotMarkPaidAgain() {
        PaymentTransaction tx = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.PERFORMED)
                .createTime(LocalDateTime.now()).performTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(tx));

        JsonNode req = request("PerformTransaction", idParams("tx1"));
        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("result").get("state").asInt()).isEqualTo(2);
        verify(paymentOrderService, never()).markPaid(any());
    }

    @Test
    void performTransaction_cancelledTransaction_returnsMinus31008() {
        PaymentTransaction tx = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.CANCELLED)
                .createTime(LocalDateTime.now()).cancelTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(tx));

        JsonNode req = request("PerformTransaction", idParams("tx1"));
        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-31008);
    }

    // ===== CancelTransaction (chargeback) =====

    @Test
    void cancelTransaction_beforePerform_marksOrderCancelledNotReversed() {
        PaymentTransaction tx = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.CREATED)
                .createTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(tx));

        JsonNode req = request("CancelTransaction", idParams("tx1"));
        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(tx.getState()).isEqualTo(PaymentTransactionState.CANCELLED);
        assertThat(response.get("result").get("state").asInt()).isEqualTo(-1);
        verify(paymentOrderService).markCancelled(order);
        verify(paymentOrderService, never()).reversePaidOrder(any());
    }

    @Test
    void cancelTransaction_afterPerform_reversesPaidOrderInsteadOfMarkCancelled() {
        PaymentTransaction tx = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.PERFORMED)
                .createTime(LocalDateTime.now()).performTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(tx));

        JsonNode req = request("CancelTransaction", idParams("tx1"));
        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(tx.getState()).isEqualTo(PaymentTransactionState.CANCELLED);
        assertThat(response.get("result").get("state").asInt()).isEqualTo(-2);
        verify(paymentOrderService).reversePaidOrder(order);
        verify(paymentOrderService, never()).markCancelled(any());
    }

    @Test
    void cancelTransaction_alreadyCancelled_isIdempotentNoSideEffects() {
        PaymentTransaction tx = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.CANCELLED)
                .createTime(LocalDateTime.now()).cancelTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(tx));

        JsonNode req = request("CancelTransaction", idParams("tx1"));
        paymeService.handle(req, validAuthHeader);

        verify(paymentOrderService, never()).markCancelled(any());
        verify(paymentOrderService, never()).reversePaidOrder(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    // ===== CheckTransaction =====

    @Test
    void checkTransaction_returnsCorrectStateMapping() {
        PaymentTransaction tx = PaymentTransaction.builder().id(10L).order(order).provider(PaymentProvider.PAYME)
                .providerTransactionId("tx1").amount(order.getAmount()).state(PaymentTransactionState.PERFORMED)
                .createTime(LocalDateTime.now()).performTime(LocalDateTime.now()).build();
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "tx1"))
                .thenReturn(Optional.of(tx));

        JsonNode req = request("CheckTransaction", idParams("tx1"));
        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("result").get("state").asInt()).isEqualTo(2);
        assertThat(response.get("result").get("transaction").asText()).isEqualTo("10");
    }

    @Test
    void checkTransaction_notFound_returnsMinus31003() {
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, "ghost"))
                .thenReturn(Optional.empty());

        JsonNode req = request("CheckTransaction", idParams("ghost"));
        ObjectNode response = paymeService.handle(req, validAuthHeader);

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-31003);
    }

    // ===== buildCheckoutUrl =====

    @Test
    void buildCheckoutUrl_encodesMerchantOrderAndAmountInBase64() {
        String url = paymeService.buildCheckoutUrl(order, null);

        assertThat(url).startsWith("https://checkout.paycom.uz/");
        String encoded = url.substring("https://checkout.paycom.uz/".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("m=" + MERCHANT_ID + ";ac.order_id=5;a=" + AMOUNT_TIYIN);
    }

    @Test
    void isEnabled_trueOnlyWhenMerchantIdAndKeyPresent() {
        assertThat(paymeService.isEnabled()).isTrue();

        ReflectionTestUtils.setField(paymeService, "key", "");
        assertThat(paymeService.isEnabled()).isFalse();
    }
}
