package behzoddev.testproject.controller.page;

import behzoddev.testproject.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordResetPageController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    // Foydalanuvchi username kiritadi — kod Telegram (agar ulangan bo'lsa)
    // yoki email orqali yuboriladi (PasswordResetService o'zi tanlaydi).
    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam String username, RedirectAttributes redirectAttributes) {
        try {
            String message = passwordResetService.requestReset(username);
            redirectAttributes.addFlashAttribute("infoMessage", message);
            redirectAttributes.addAttribute("username", username);
            return "redirect:/reset-password";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("forgotPasswordError", e.getMessage());
            return "redirect:/forgot-password";
        }
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String confirmReset(
            @RequestParam String username,
            @RequestParam String code,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        try {
            passwordResetService.confirmReset(username, code, newPassword, confirmPassword);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "✅ Parol muvaffaqiyatli yangilandi. Endi yangi parol bilan kiring."
            );
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("resetPasswordError", e.getMessage());
            redirectAttributes.addAttribute("username", username);
            return "redirect:/reset-password";
        }
    }
}
