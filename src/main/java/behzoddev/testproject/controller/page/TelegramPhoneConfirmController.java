package behzoddev.testproject.controller.page;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.PhoneNumberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.SessionFlashMapManager;

// "Telegram orqali kirish"da HAR SAFAR (hisobda allaqachon bor bo'lsa
// ham) telefon raqamni so'rash/tasdiqlash bosqichi — MAJBURIY
// (foydalanuvchi so'rovi, 2026-09-06: "Har safar telegram orqali
// kirishda telefon so'rasin"). Haqiqiy autentifikatsiya (SecurityContext)
// shu bosqich MUVAFFAQIYATLI yakunlangandan KEYIN o'rnatiladi —
// TelegramWidgetLoginController'da resolveUser() dan keyin darhol emas,
// shu oraliq sahifaga (permitAll) yo'naltiriladi, foydalanuvchi ID'si
// vaqtincha sessiyada (PENDING_USER_SESSION_KEY) saqlanadi.
@Slf4j
@Controller
@RequiredArgsConstructor
public class TelegramPhoneConfirmController {

    public static final String PENDING_USER_SESSION_KEY = "telegram_pending_user_id";

    private final UserRepository userRepository;
    private final PhoneNumberService phoneNumberService;

    @GetMapping("/telegram-phone-confirm")
    public String showForm(HttpServletRequest request, Model model) {
        User user = pendingUser(request);
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("countries", phoneNumberService.listCountries());
        model.addAttribute("existingPhoneCountryIso", phoneNumberService.regionOf(user.getPhoneNumber()));
        model.addAttribute("existingPhoneNational", phoneNumberService.nationalNumberOf(user.getPhoneNumber()));
        return "telegramPhoneConfirm";
    }

    @PostMapping("/telegram-phone-confirm")
    public String submit(@RequestParam String phoneCountry,
                          @RequestParam String phoneNumber,
                          HttpServletRequest request, HttpServletResponse response,
                          RedirectAttributes redirectAttributes) {
        User user = pendingUser(request);
        if (user == null) {
            return "redirect:/login";
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            redirectAttributes.addFlashAttribute("phoneError", "❌ Telefon raqamni kiriting.");
            return "redirect:/telegram-phone-confirm";
        }

        String normalized;
        try {
            normalized = phoneNumberService.normalize(phoneCountry, phoneNumber);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("phoneError", e.getMessage());
            return "redirect:/telegram-phone-confirm";
        }

        user.setPhoneNumber(normalized);
        userRepository.save(user);

        request.getSession(true).removeAttribute(PENDING_USER_SESSION_KEY);

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        log.info("Telegram orqali kirish: telefon tasdiqlandi, user={}", user.getUsername());
        return "redirect:/index";
    }

    private User pendingUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long pendingUserId = session != null ? (Long) session.getAttribute(PENDING_USER_SESSION_KEY) : null;
        if (pendingUserId == null) {
            return null;
        }
        return userRepository.findById(pendingUserId).orElse(null);
    }
}
