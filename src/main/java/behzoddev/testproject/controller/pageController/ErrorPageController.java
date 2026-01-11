package behzoddev.testproject.controller.pageController;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ErrorPageController {
    @GetMapping("/app-error")
    public String error(@RequestParam String msg, Model model) {
        model.addAttribute("errorMessage", msg);
        return "app-error";
    }
}
