package behzoddev.testproject.controller.page;

import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfilePageController {
    @GetMapping
    public String getProfilePage() {
        return "profile/profile";
    } //profile.html

    @GetMapping("/test/{id}")
    public String viewTest(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            Model model) {
        // просто передаём id, проверка прав будет в API
        model.addAttribute("testSessionId", id); // передаем ID теста в HTML
        return "profile/test"; // Thymeleaf-шаблон: resources/templates/profile/test.html
    }

}
