package behzoddev.testproject.service.payment;

import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.PaymentTransactionRepository;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.PaymentTransaction;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.entity.enums.PaymentProvider;
import behzoddev.testproject.entity.enums.PaymentTransactionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Click Merchant API (Shop API v2) — bitta URL, {@code action} parametriga
 * qarab Prepare (0) yoki Complete (1) sifatida ishlaydi.
 * <p>
 * ⚠️ MUHIM: https://docs.click.uz/ rasmiy hujjatiga asoslangan, lekin
 * https://merchant.click.uz test (sandbox) muhitida haqiqiy so'rovlar bilan
 * TEKSHIRILMAGUNCHA production'da ishlatilmasin — xususan xato kodlari
 * chegara holatlarini rasmiy hujjat bilan solishtiring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickService {

    private static final int ACTION_PREPARE = 0;
    private static final int ACTION_COMPLETE = 1;

    @Value("${app.payments.click.service-id:}")
    private String serviceId;

    @Value("${app.payments.click.merchant-id:}")
    private String merchantId;

    @Value("${app.payments.click.secret-key:}")
    private String secretKey;

    @Value("${app.payments.click.pay-base-url:https://my.click.uz/services/pay}")
    private String payBaseUrl;

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentOrderService paymentOrderService;

    public boolean isEnabled() {
        return notBlank(serviceId) && notBlank(merchantId) && notBlank(secretKey);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // https://docs.click.uz/ -> "to'g'ridan-to'g'ri havola orqali to'lov" (Checkout link).
    public String buildPayUrl(PaymentOrder order, String returnUrl) {
        return UriComponentsBuilder.fromUriString(payBaseUrl)
                .queryParam("service_id", serviceId)
                .queryParam("merchant_id", merchantId)
                .queryParam("amount", order.getAmount().toPlainString())
                .queryParam("transaction_param", order.getId())
                .queryParamIfPresent("return_url", java.util.Optional.ofNullable(returnUrl))
                .build().toUriString();
    }

    // Click'ning ikkala chaqiruvi ham (Prepare/Complete) shu bitta metod
    // orqali keladi — form-urlencoded parametrlar Map sifatida.
    @Transactional
    public Map<String, Object> handle(Map<String, String> params) {
        int action = parseInt(params.get("action"), -1);

        try {
            if (!verifySign(params, action)) {
                return errorResponse(params, -1, "SIGN CHECK FAILED!");
            }

            return switch (action) {
                case ACTION_PREPARE -> prepare(params);
                case ACTION_COMPLETE -> complete(params);
                default -> errorResponse(params, -3, "Action not found");
            };
        } catch (ClickException e) {
            return errorResponse(params, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Click webhook ichki xatolik", e);
            return errorResponse(params, -7, "Failed to update user");
        }
    }

    private Map<String, Object> prepare(Map<String, String> params) {
        String clickTransId = params.get("click_trans_id");
        PaymentOrder order = findOrderOrThrow(params.get("merchant_trans_id"));
        validateAmount(order, params.get("amount"));

        if (order.getStatus() != PaymentOrderStatus.CREATED) {
            throw new ClickException(-4, "Already paid");
        }

        Optional<PaymentTransaction> existing = paymentTransactionRepository
                .findByProviderAndProviderTransactionId(PaymentProvider.CLICK, clickTransId);

        PaymentTransaction tx = existing.orElseGet(() -> PaymentTransaction.builder()
                .order(order)
                .provider(PaymentProvider.CLICK)
                .providerTransactionId(clickTransId)
                .amount(order.getAmount())
                .state(PaymentTransactionState.CREATED)
                .createTime(LocalDateTime.now())
                .build());

        paymentTransactionRepository.save(tx);
        log.info("Click Prepare: order={}, clickTransId={}", order.getId(), clickTransId);

        Map<String, Object> result = baseResponse(params);
        result.put("merchant_prepare_id", tx.getId());
        result.put("error", 0);
        result.put("error_note", "Success");
        return result;
    }

    private Map<String, Object> complete(Map<String, String> params) {
        String clickTransId = params.get("click_trans_id");
        int incomingError = parseInt(params.get("error"), 0);

        PaymentTransaction tx = paymentTransactionRepository
                .findByProviderAndProviderTransactionId(PaymentProvider.CLICK, clickTransId)
                .orElseThrow(() -> new ClickException(-6, "Transaction does not exist"));

        // Click o'zi to'lov muvaffaqiyatsiz bo'lganini xabar qilmoqda (masalan
        // foydalanuvchi bekor qilgan) — bizning tomonimizdan xato emas, faqat
        // tranzaksiyani bekor qilib, qabul qilinganini tasdiqlaymiz.
        if (incomingError < 0) {
            if (tx.getState() != PaymentTransactionState.CANCELLED) {
                tx.setState(PaymentTransactionState.CANCELLED);
                tx.setCancelTime(LocalDateTime.now());
                paymentTransactionRepository.save(tx);
                paymentOrderService.markCancelled(tx.getOrder());
            }
            Map<String, Object> result = baseResponse(params);
            result.put("merchant_confirm_id", tx.getId());
            result.put("error", 0);
            result.put("error_note", "Success");
            return result;
        }

        if (tx.getState() == PaymentTransactionState.CANCELLED) {
            throw new ClickException(-9, "Transaction cancelled");
        }

        if (tx.getState() != PaymentTransactionState.PERFORMED) {
            tx.setState(PaymentTransactionState.PERFORMED);
            tx.setPerformTime(LocalDateTime.now());
            paymentTransactionRepository.save(tx);
            paymentOrderService.markPaid(tx.getOrder());
            log.info("Click Complete: order={}, clickTransId={}", tx.getOrder().getId(), clickTransId);
        }

        Map<String, Object> result = baseResponse(params);
        result.put("merchant_confirm_id", tx.getId());
        result.put("error", 0);
        result.put("error_note", "Success");
        return result;
    }

    private boolean verifySign(Map<String, String> params, int action) {
        String clickTransId = params.getOrDefault("click_trans_id", "");
        String merchantTransId = params.getOrDefault("merchant_trans_id", "");
        String amount = params.getOrDefault("amount", "");
        String signTime = params.getOrDefault("sign_time", "");
        String signString = params.getOrDefault("sign_string", "");

        String raw;
        if (action == ACTION_COMPLETE) {
            String merchantPrepareId = params.getOrDefault("merchant_prepare_id", "");
            raw = clickTransId + serviceId + secretKey + merchantTransId + merchantPrepareId + amount + action + signTime;
        } else {
            raw = clickTransId + serviceId + secretKey + merchantTransId + amount + action + signTime;
        }

        String expected = md5(raw);
        return expected.equalsIgnoreCase(signString);
    }

    private PaymentOrder findOrderOrThrow(String merchantTransId) {
        try {
            return paymentOrderRepository.findById(Long.parseLong(merchantTransId))
                    .orElseThrow(() -> new ClickException(-5, "User does not exist"));
        } catch (NumberFormatException e) {
            throw new ClickException(-5, "User does not exist");
        }
    }

    private void validateAmount(PaymentOrder order, String amountStr) {
        BigDecimal expected = order.getAmount();
        BigDecimal actual;
        try {
            actual = new BigDecimal(amountStr);
        } catch (Exception e) {
            throw new ClickException(-2, "Incorrect parameter amount");
        }

        // Click summani "so'm.tiyin" ko'rinishida (masalan 50000.00) yuboradi —
        // dumaloqlash farqlarini yumshoq (2 xona) solishtiramiz.
        if (expected.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(actual.setScale(2, java.math.RoundingMode.HALF_UP)) != 0) {
            throw new ClickException(-2, "Incorrect parameter amount");
        }
    }

    private Map<String, Object> baseResponse(Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("click_trans_id", parseLong(params.get("click_trans_id")));
        result.put("merchant_trans_id", params.get("merchant_trans_id"));
        return result;
    }

    private Map<String, Object> errorResponse(Map<String, String> params, int error, String errorNote) {
        Map<String, Object> result = baseResponse(params);
        result.put("error", error);
        result.put("error_note", errorNote);
        return result;
    }

    private int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 mavjud emas", e);
        }
    }
}
