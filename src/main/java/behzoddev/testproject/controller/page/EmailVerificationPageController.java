package behzoddev.testproject.controller.page;

import behzoddev.testproject.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class EmailVerificationPageController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping("/verify-email")
    public String verifyEmailPage() {
        return "verify-email";
    }

    @PostMapping("/verify-email")
    public String confirm(
            @RequestParam String username,
            @RequestParam String code,
            RedirectAttributes redirectAttributes
    ) {
        try {
            emailVerificationService.confirm(username, code);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "✅ Email muvaffaqiyatli tasdiqlandi! Endi tizimga kirishingiz mumkin."
            );
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("verifyEmailError", e.getMessage());
            redirectAttributes.addAttribute("username", username);
            return "redirect:/verify-email";
        }
    }

    @PostMapping("/verify-email/resend")
    public String resend(@RequestParam String username, RedirectAttributes redirectAttributes) {
        try {
            String message = emailVerificationService.resend(username);
            redirectAttributes.addFlashAttribute("infoMessage", message);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("verifyEmailError", e.getMessage());
        }
        redirectAttributes.addAttribute("username", username);
        return "redirect:/verify-email";
    }
}
