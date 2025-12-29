package behzoddev.testproject.controller;

import behzoddev.testproject.dto.LoginDto;
import behzoddev.testproject.dto.RegisterDto;
import behzoddev.testproject.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UserMvcController {

    private final UserServiceImpl userService;

    @PostMapping("/registration")
    public String register(@ModelAttribute RegisterDto dto,
                        RedirectAttributes redirectAttributes) {
        userService.register(dto);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "User registered successfully"
        );

        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/signin")
    public String login(@ModelAttribute LoginDto dto) {
        userService.checkCredentials(dto);
        return "redirect:/subjects";
    }

    @GetMapping("/subjects")
    public String login_success() {
        return "subjects";
    }
}


