package behzoddev.testproject.controller.api.payment;

import behzoddev.testproject.service.payment.PaymeService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Payme'ning o'z serverlari shu endpoint'ga JSON-RPC 2.0 so'rov yuboradi
// (autentifikatsiya Basic Auth header orqali, sessiya/login shart emas —
// shuning uchun SecurityConfig'da permitAll). Merchant kabinetida
// (business.payme.uz) callback URL sifatida shu manzil ko'rsatiladi:
// https://<domen>/api/payments/payme/webhook
@RestController
@RequiredArgsConstructor
public class PaymeWebhookController {

    private final PaymeService paymeService;

    @PostMapping("/api/payments/payme/webhook")
    public ResponseEntity<ObjectNode> webhook(
            @RequestBody JsonNode request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        // Payme spetsifikatsiyasi: xato bo'lsa ham har doim HTTP 200 va
        // JSON-RPC "error" ob'ekti bilan javob berish kutiladi.
        return ResponseEntity.ok(paymeService.handle(request, authHeader));
    }
}
