package behzoddev.testproject.controller.page;

import behzoddev.testproject.dto.user.RegisterDto;
import behzoddev.testproject.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UserMvcController {

    private final UserServiceImpl userService;

    @PostMapping("/registration")
    public String register(@ModelAttribute RegisterDto dto,
                           RedirectAttributes redirectAttributes) {
        try {
            userService.register(dto);
        } catch (Exception e) {
            // Masalan: username/email band, parollar mos kelmadi va h.k.
            redirectAttributes.addFlashAttribute("registrationError", e.getMessage());
            return "redirect:/registration";
        }

        // Email kiritilmagan bo'lsa — akkaunt darhol faollashtirilgan
        // (UserServiceImpl.register), tasdiqlash bosqichi shart emas —
        // to'g'ridan-to'g'ri /login'ga. Email kiritilgan bo'lsa — hozirgidek
        // /verify-email'ga (tasdiqlash kodi kutiladi).
        boolean hasEmail = dto.email() != null && !dto.email().isBlank();
        if (!hasEmail) {
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "✅ Ro'yxatdan o'tish muvaffaqiyatli! Endi tizimga kirishingiz mumkin."
            );
            return "redirect:/login";
        }

        redirectAttributes.addFlashAttribute(
                "infoMessage",
                "✅ Ro'yxatdan o'tish muvaffaqiyatli! Emailingizga yuborilgan tasdiqlash kodini kiriting."
        );
        redirectAttributes.addAttribute("username", dto.username());

        return "redirect:/verify-email";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/index")
    public String login_success() {
        return "index";
    }

}