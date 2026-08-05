package behzoddev.testproject.controller.page;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PaymentHistoryPageController {

    // OWNER uchun to'lov tarixi/hisobot sahifasi — Subscription
    // ma'lumotlariga asoslangan (jami tushum, oylik dinamika, to'liq tarix).
    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public String openPaymentHistoryPage(Model model, Authentication authentication) {

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter("ROLE_OWNER"::equals)
                .findFirst()
                .orElse("UNKNOWN");

        model.addAttribute("role", role);

        return "paymentHistoryPage";
    }
}
