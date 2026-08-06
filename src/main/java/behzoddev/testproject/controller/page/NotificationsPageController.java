package behzoddev.testproject.controller.page;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.stream.Collectors;

// Bildirishnomalarning to'liq sahifasi — "Yangi" va "O'qilgan" ikkita
// tab'ga bo'lingan (navbar'dagi qo'ng'iroq ustidagi ochiladigan panel endi
// faqat qisqa "kirish darvozasi", to'liq ro'yxat va statistika shu yerda).
@Controller
public class NotificationsPageController {

    @GetMapping("/notifications")
    public String openNotificationsPage(Model model, Authentication authentication) {

        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .collect(Collectors.joining(","));

        model.addAttribute("role", roles);

        return "notifications";
    }
}
