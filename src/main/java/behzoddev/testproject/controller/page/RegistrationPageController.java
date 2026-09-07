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

    // "Telegram orqali kirish" tugmasi uchun (registration.html) — bir xil
    // g'oya UserMvcController#login bilan (o'zbekcha custom tugma,
    // Telegram'ning tayyor widget'i o'rniga).
    @Value("${telegram.bot.token}")
    private String telegramBotToken;

    private final PhoneNumberService phoneNumberService;

    @GetMapping("/registration")
    public String registration(Model model) {
        model.addAttribute("telegramBotId", telegramBotToken.split(":")[0]);
        // Telefon maydoni endi MAJBURIY (foydalanuvchi so'rovi, 2026-09-07)
        // — davlat tanlash widget'i uchun (country-picker.js, /profile'da
        // ishlatilgani bilan bir xil), ro'yxat SERVER tomonidan shu yerda
        // beriladi (bu sahifa anonim — /api/profile/phone/countries
        // autentifikatsiya talab qiladi, bu yerda ishlatib bo'lmaydi).
        model.addAttribute("countries", phoneNumberService.listCountries());
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
