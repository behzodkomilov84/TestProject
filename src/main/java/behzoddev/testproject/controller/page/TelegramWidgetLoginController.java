package behzoddev.testproject.controller.page;

import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.service.TelegramWidgetLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.util.Map;

// Telegram Login Widget'dan kelgan redirect shu yerga tushadi
// (foydalanuvchi so'rovi, 2026-09-06: login/registratsiya sahifasiga
// "Telegram orqali kirish" tugmasi). SecurityConfig'da permitAll — hali
// login qilinmagan holatda keladi. Sessiyani qo'lda o'rnatish
// (TelegramAutoLoginController'dagi bilan bir xil andoza) — bu yerda
// formLogin filtri ishlamaydi, chunki parol yo'q, Telegram'ning o'zi
// (TelegramWidgetLoginService#resolveUser) shaxsni isbotlagan.
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

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        log.info("Telegram widget orqali login: user={}", user.getUsername());
        return "redirect:/index";
    }
}
