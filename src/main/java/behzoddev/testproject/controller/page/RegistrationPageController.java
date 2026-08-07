package behzoddev.testproject.controller.page;

import behzoddev.testproject.service.PhoneNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class RegistrationPageController {

    private final PhoneNumberService phoneNumberService;

    @GetMapping("/registration")
    public String registration(Model model) {
        model.addAttribute("countries", phoneNumberService.listCountries());
        return "registration";
    }

    @GetMapping("/")
    public String startPage() {
        return "/login";
    }
}
