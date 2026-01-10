package behzoddev.testproject.controller.pageController;

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

        // Берём первую роль пользователя (или можно сделать список всех)
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("UNKNOWN");

        model.addAttribute("role", role);

        return "users"; // Thymeleaf шаблон users.html
    }

}
