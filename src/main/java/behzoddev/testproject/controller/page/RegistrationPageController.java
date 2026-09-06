package behzoddev.testproject.controller.page;

import behzoddev.testproject.service.PhoneNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class RegistrationPageController {

    private final PhoneNumberService phoneNumberService;

    // "Telegram orqali kirish" widget'i uchun (registration.html) — bot
    // nomi hardcode qilinmasin (application.yaml'dagi bilan bir xil manba).
    @Value("${telegram.bot.username}")
    private String telegramBotUsername;

    @GetMapping("/registration")
    public String registration(Model model) {
        model.addAttribute("countries", phoneNumberService.listCountries());
        model.addAttribute("telegramBotUsername", telegramBotUsername);
        return "registration";
    }

    // "/" permitAll bo'lgani uchun login qilgan foydalanuvchi ham shu yerga
    // tushadi. Ilgari har doim "/login" Thymeleaf shabloni sifatida
    // qaytarilardi (yetakchi "/" bilan) — bu login qilmagan userlar uchun
    // tasodifan ishlab turgan bo'lsa-da, login qilganlar uchun shablonni
    // topa olmay 500 xato berardi. Endi ikkalasi ham redirect orqali,
    // holatiga qarab (login sahifasi yoki index) yo'naltiriladi.
    @GetMapping("/")
    public String startPage(Authentication authentication) {
        boolean loggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        return loggedIn ? "redirect:/index" : "redirect:/login";
    }
}
