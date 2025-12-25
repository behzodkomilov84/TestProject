package behzoddev.testproject.controller;

import behzoddev.testproject.dto.RegisterDto;
import behzoddev.testproject.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class UserMvcController {

    private final UserServiceImpl userService;

    @PostMapping("/registration")
    public String register(@ModelAttribute RegisterDto dto) {
        userService.register(dto);
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}


