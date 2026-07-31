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
public class UserManagerPageController {


    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public String openUserManagerPage(Model model, Authentication authentication) {

        // Bu sahifaga @PreAuthorize orqali faqat ROLE_OWNER kira oladi, lekin
        // dual-role tufayli foydalanuvchida boshqa rollar ham bo'lishi mumkin
        // (masalan ROLE_ADMIN). Shu sabab "findFirst()" o'rniga aynan
        // ROLE_OWNER'ni qidiramiz — aks holda Set tartibi tasodifiy bo'lgani
        // uchun boshqa rol birinchi chiqib, JS'dagi tekshiruv xato ishlashi mumkin edi.
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter("ROLE_OWNER"::equals)
                .findFirst()
                .orElse("UNKNOWN");

        model.addAttribute("role", role);

        return "userManagerPage"; // Thymeleaf шаблон userManagerPage.html
    }

}
