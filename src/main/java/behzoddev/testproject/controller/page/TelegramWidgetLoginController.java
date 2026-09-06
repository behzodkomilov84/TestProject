package behzoddev.testproject.controller.page;

import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.service.TelegramWidgetLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.util.Map;

// Telegram Login Widget'dan kelgan redirect shu yerga tushadi
// (foydalanuvchi so'rovi, 2026-09-06: login/registratsiya sahifasiga
// "Telegram orqali kirish" tugmasi). SecurityConfig'da permitAll — hali
// login qilinmagan holatda keladi.
//
// DIQQAT: bu yerda ENDI to'liq autentifikatsiya (SecurityContext)
// o'rnatilMAYDI — foydalanuvchi so'rovi bo'yicha (2026-09-06: "Har safar
// telegram orqali kirishda telefon so'rasin", majburiy, hisobda
// allaqachon bor bo'lsa ham) resolveUser() dan keyin
// TelegramPhoneConfirmController'ga yo'naltiriladi, u yerda telefon
// tasdiqlangandan KEYIN chinakam login sodir bo'ladi.
@Slf4j
@Controller
@RequiredArgsConstructor
public class TelegramWidgetLoginController {

    private final TelegramWidgetLoginService telegramWidgetLoginService;

    @GetMapping("/telegram-login")
    public String telegramLogin(@RequestParam Map<String, String> params,
                                 @AuthenticationPrincipal(errorOnInvalidType = false) User currentUser,
                                 HttpServletRequest request, HttpServletResponse response) {
        User user;
        try {
            user = telegramWidgetLoginService.resolveUser(params, currentUser);
        } catch (TelegramWidgetLoginService.InvalidTelegramAuthException e) {
            log.warn("Telegram widget orqali login rad etildi: {}", e.getMessage());
            var flashMap = new FlashMap();
            flashMap.put("LOGIN_ERROR", e.getMessage());
            new SessionFlashMapManager().saveOutputFlashMap(flashMap, request, response);
            return "redirect:/login";
        }

        var session = request.getSession(true);
        session.setAttribute(TelegramPhoneConfirmController.PENDING_USER_SESSION_KEY, user.getId());
        log.info("[TG-DEBUG] telegramLogin: sessionId={}, user.getId()={}, user.getTelegramId()={}, user={}",
                session.getId(), user.getId(), user.getTelegramId(), user.getUsername());
        return "redirect:/telegram-phone-confirm";
    }
}
