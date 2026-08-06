package behzoddev.testproject.service.payment;

import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.PaymentTransactionRepository;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.PaymentTransaction;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.entity.enums.PaymentProvider;
import behzoddev.testproject.entity.enums.PaymentTransactionState;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Payme (Paycom) Merchant API — JSON-RPC 2.0 protokoli.
 * <p>
 * ⚠️ MUHIM: bu implementatsiya Payme'ning rasmiy hujjatlariga
 * (https://developer.help.paycom.uz/) asoslanib yozilgan, lekin haqiqiy
 * merchant kabinetidan (https://business.payme.uz) test (sandbox) muhitida
 * ULARNING SERTIFIKATSIYA TEKSHIRUVIDAN o'tkazilmaguncha ishonchli deb
 * hisoblanmasligi kerak — ayniqsa xato kodlari va chegara holatlari
 * (masalan tranzaksiya "muddati o'tishi" ~12 soat) rasmiy hujjat bilan
 * solishtirib tekshirilishi shart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymeService {

    @Value("${app.payments.payme.merchant-id:}")
    private String merchantId;

    // "Paycom" login bilan keladigan Basic Auth parolining o'zi shu kalit.
    @Value("${app.payments.payme.key:}")
    private String key;

    @Value("${app.payments.payme.checkout-base-url:https://checkout.paycom.uz}")
    private String checkoutBaseUrl;

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentOrderService paymentOrderService;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return merchantId != null && !merchantId.isBlank() && key != null && !key.isBlank();
    }

    // https://checkout.paycom.uz/{base64("m=...;ac.order_id=...;a=...")}
    public String buildCheckoutUrl(PaymentOrder order, String returnUrl) {
        long amountTiyin = order.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        StringBuilder raw = new StringBuilder("m=").append(merchantId)
                .append(";ac.order_id=").append(order.getId())
                .append(";a=").append(amountTiyin);
        if (returnUrl != null && !returnUrl.isBlank()) {
            raw.append(";c=").append(returnUrl);
        }
        String encoded = Base64.getEncoder().encodeToString(raw.toString().getBytes(StandardCharsets.UTF_8));
        return checkoutBaseUrl + "/" + encoded;
    }

    private boolean checkAuth(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(6)), StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx < 0) return false;
            return key.equals(decoded.substring(idx + 1));
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public ObjectNode handle(JsonNode request, String authHeader) {
        ObjectNode response = objectMapper.createObjectNode();
        JsonNode idNode = request.get("id");
        if (idNode != null) response.set("id", idNode); else response.putNull("id");

        if (!checkAuth(authHeader)) {
            return error(response, -32504, "Insufficient privilege to perform this method.", null);
        }

        String method = request.path("method").asText("");
        JsonNode params = request.path("params");

        try {
            return switch (method) {
                case "CheckPerformTransaction" -> checkPerformTransaction(params, response);
                case "CreateTransaction" -> createTransaction(params, response);
                case "PerformTransaction" -> performTransaction(params, response);
                case "CancelTransaction" -> cancelTransaction(params, response);
                case "CheckTransaction" -> checkTransaction(params, response);
                case "GetStatement" -> getStatement(params, response);
                default -> error(response, -32601, "Method not found.", null);
            };
        } catch (PaymeException e) {
            return error(response, e.getCode(), e.getMessage(), e.getField());
        } catch (Exception e) {
            log.error("Payme webhook ichki xatolik", e);
            return error(response, -32400, "System error.", null);
        }
    }

    private ObjectNode checkPerformTransaction(JsonNode params, ObjectNode response) {
        PaymentOrder order = findOrderOrThrow(params);
        validateAmount(order, params);

        ObjectNode result = response.putObject("result");
        result.put("allow", true);
        return response;
    }

    private ObjectNode createTransaction(JsonNode params, ObjectNode response) {
        String paymeTxId = params.path("id").asText();
        if (paymeTxId.isBlank()) {
            throw new PaymeException(-32600, "params.id is required");
        }

        Optional<PaymentTransaction> existing = paymentTransactionRepository
                .findByProviderAndProviderTransactionId(PaymentProvider.PAYME, paymeTxId);

        if (existing.isPresent()) {
            PaymentTransaction tx = existing.get();

            if (tx.getState() == PaymentTransactionState.CANCELLED) {
                throw new PaymeException(-31008, "Transaction was cancelled.");
            }

            ObjectNode result = response.putObject("result");
            result.put("create_time", toEpochMilli(tx.getCreateTime()));
            result.put("transaction", tx.getId().toString());
            result.put("state", tx.getState() == PaymentTransactionState.PERFORMED ? 2 : 1);
            return response;
        }

        PaymentOrder order = findOrderOrThrow(params);
        validateAmount(order, params);

        if (order.getStatus() != PaymentOrderStatus.CREATED) {
            throw new PaymeException(-31050, "Order is not payable (already paid or cancelled).", "order_id");
        }

        // Ushbu order uchun boshqa faol (CREATED/PERFORMED) tranzaksiya bormi —
        // bo'lsa, ikkinchi marta yangi tranzaksiya ochishga ruxsat bermaymiz.
        List<PaymentTransaction> active = paymentTransactionRepository
                .findByOrder_IdAndStateNot(order.getId(), PaymentTransactionState.CANCELLED);
        if (!active.isEmpty()) {
            throw new PaymeException(-31050, "Order already has an active transaction.", "order_id");
        }

        PaymentTransaction tx = PaymentTransaction.builder()
                .order(order)
                .provider(PaymentProvider.PAYME)
                .providerTransactionId(paymeTxId)
                .amount(order.getAmount())
                .state(PaymentTransactionState.CREATED)
                .createTime(LocalDateTime.now())
                .build();

        paymentTransactionRepository.save(tx);
        log.info("Payme CreateTransaction: order={}, txId={}", order.getId(), paymeTxId);

        ObjectNode result = response.putObject("result");
        result.put("create_time", toEpochMilli(tx.getCreateTime()));
        result.put("transaction", tx.getId().toString());
        result.put("state", 1);
        return response;
    }

    private ObjectNode performTransaction(JsonNode params, ObjectNode response) {
        PaymentTransaction tx = findTransactionOrThrow(params);

        if (tx.getState() == PaymentTransactionState.PERFORMED) {
            ObjectNode result = response.putObject("result");
            result.put("transaction", tx.getId().toString());
            result.put("perform_time", toEpochMilli(tx.getPerformTime()));
            result.put("state", 2);
            return response;
        }

        if (tx.getState() == PaymentTransactionState.CANCELLED) {
            throw new PaymeException(-31008, "Transaction was cancelled.");
        }

        tx.setState(PaymentTransactionState.PERFORMED);
        tx.setPerformTime(LocalDateTime.now());
        paymentTransactionRepository.save(tx);

        paymentOrderService.markPaid(tx.getOrder());
        log.info("Payme PerformTransaction: order={}, txId={}", tx.getOrder().getId(), tx.getProviderTransactionId());

        ObjectNode result = response.putObject("result");
        result.put("transaction", tx.getId().toString());
        result.put("perform_time", toEpochMilli(tx.getPerformTime()));
        result.put("state", 2);
        return response;
    }

    private ObjectNode cancelTransaction(JsonNode params, ObjectNode response) {
        PaymentTransaction tx = findTransactionOrThrow(params);
        Integer reason = params.hasNonNull("reason") ? params.get("reason").asInt() : null;

        if (tx.getState() != PaymentTransactionState.CANCELLED) {
            boolean wasPerformed = tx.getState() == PaymentTransactionState.PERFORMED;

            tx.setState(PaymentTransactionState.CANCELLED);
            tx.setCancelTime(LocalDateTime.now());
            tx.setCancelReason(reason);
            paymentTransactionRepository.save(tx);

            if (wasPerformed) {
                paymentOrderService.reversePaidOrder(tx.getOrder());
            } else {
                paymentOrderService.markCancelled(tx.getOrder());
            }

            log.info("Payme CancelTransaction: order={}, txId={}, sababPerformedEdi={}",
                    tx.getOrder().getId(), tx.getProviderTransactionId(), wasPerformed);
        }

        ObjectNode result = response.putObject("result");
        result.put("transaction", tx.getId().toString());
        result.put("cancel_time", toEpochMilli(tx.getCancelTime()));
        result.put("state", tx.getPerformTime() != null ? -2 : -1);
        return response;
    }

    private ObjectNode checkTransaction(JsonNode params, ObjectNode response) {
        PaymentTransaction tx = findTransactionOrThrow(params);

        int state = switch (tx.getState()) {
            case CREATED -> 1;
            case PERFORMED -> 2;
            case CANCELLED -> tx.getPerformTime() != null ? -2 : -1;
        };

        ObjectNode result = response.putObject("result");
        result.put("create_time", toEpochMilli(tx.getCreateTime()));
        result.put("perform_time", toEpochMilli(tx.getPerformTime()));
        result.put("cancel_time", toEpochMilli(tx.getCancelTime()));
        result.put("transaction", tx.getId().toString());
        result.put("state", state);
        if (tx.getCancelReason() != null) {
            result.put("reason", tx.getCancelReason());
        } else {
            result.putNull("reason");
        }
        return response;
    }

    private ObjectNode getStatement(JsonNode params, ObjectNode response) {
        LocalDateTime from = LocalDateTime.ofInstant(Instant.ofEpochMilli(params.path("from").asLong()), ZoneOffset.UTC);
        LocalDateTime to = LocalDateTime.ofInstant(Instant.ofEpochMilli(params.path("to").asLong()), ZoneOffset.UTC);

        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByProviderAndCreateTimeBetween(PaymentProvider.PAYME, from, to);

        var arr = response.putObject("result").putArray("transactions");
        for (PaymentTransaction tx : transactions) {
            ObjectNode t = arr.addObject();
            t.put("id", tx.getProviderTransactionId());
            t.put("time", toEpochMilli(tx.getCreateTime()));
            t.put("amount", tx.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact());
            t.putObject("account").put("order_id", tx.getOrder().getId().toString());
            t.put("create_time", toEpochMilli(tx.getCreateTime()));
            t.put("perform_time", toEpochMilli(tx.getPerformTime()));
            t.put("cancel_time", toEpochMilli(tx.getCancelTime()));
            t.put("transaction", tx.getId().toString());
            int state = switch (tx.getState()) {
                case CREATED -> 1;
                case PERFORMED -> 2;
                case CANCELLED -> tx.getPerformTime() != null ? -2 : -1;
            };
            t.put("state", state);
            if (tx.getCancelReason() != null) t.put("reason", tx.getCancelReason());
        }
        return response;
    }

    private PaymentOrder findOrderOrThrow(JsonNode params) {
        JsonNode accountNode = params.path("account");
        String orderIdStr = accountNode.path("order_id").asText(null);

        if (orderIdStr == null || orderIdStr.isBlank()) {
            throw new PaymeException(-31050, "Order not found.", "order_id");
        }

        try {
            return paymentOrderRepository.findById(Long.parseLong(orderIdStr))
                    .orElseThrow(() -> new PaymeException(-31050, "Order not found.", "order_id"));
        } catch (NumberFormatException e) {
            throw new PaymeException(-31050, "Order not found.", "order_id");
        }
    }

    private void validateAmount(PaymentOrder order, JsonNode params) {
        long expectedTiyin = order.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        long actualTiyin = params.path("amount").asLong(-1);

        if (actualTiyin != expectedTiyin) {
            throw new PaymeException(-31001, "Incorrect amount.", "amount");
        }
    }

    private PaymentTransaction findTransactionOrThrow(JsonNode params) {
        String paymeTxId = params.path("id").asText();
        return paymentTransactionRepository.findByProviderAndProviderTransactionId(PaymentProvider.PAYME, paymeTxId)
                .orElseThrow(() -> new PaymeException(-31003, "Transaction not found."));
    }

    private ObjectNode error(ObjectNode response, int code, String message, String field) {
        ObjectNode err = response.putObject("error");
        err.put("code", code);
        ObjectNode msg = err.putObject("message");
        msg.put("uz", message);
        msg.put("ru", message);
        msg.put("en", message);
        if (field != null) {
            err.put("data", field);
        }
        return response;
    }

    private Long toEpochMilli(LocalDateTime time) {
        return time == null ? 0L : time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
