package behzoddev.testproject.controller.api.payment;

import behzoddev.testproject.service.payment.ClickService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Click'ning o'z serverlari shu endpoint'ga form-urlencoded POST yuboradi
// (Prepare va Complete — "action" parametri orqali ajratiladi, alohida
// URL shart emas). Merchant kabinetida (merchant.click.uz) callback URL
// sifatida shu manzil ko'rsatiladi: https://<domen>/api/payments/click/webhook
@RestController
@RequiredArgsConstructor
public class ClickWebhookController {

    private final ClickService clickService;

    @PostMapping(value = "/api/payments/click/webhook", consumes = "application/x-www-form-urlencoded")
    public Map<String, Object> webhook(@RequestParam Map<String, String> params) {
        return clickService.handle(params);
    }
}
