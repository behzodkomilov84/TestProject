package behzoddev.testproject.controller.page;

import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.GoogleLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

// "Google orqali kirish" — oddiy Authorization Code oqimi (foydalanuvchi
// so'rovi, 2026-09-06). SecurityConfig'da "/oauth2/google/**" permitAll —
// hali login qilinmagan holatda keladi. Sessiyani qo'lda o'rnatish
// (TelegramWidgetLoginController bilan bir xil andoza).
@Slf4j
@Controller
@RequiredArgsConstructor
public class GoogleLoginController {

    private static final String STATE_SESSION_KEY = "google_oauth_state";

    private final GoogleLoginService googleLoginService;

    @GetMapping("/oauth2/google/authorize")
    public String authorize(HttpServletRequest request, HttpServletResponse response) {
        if (!googleLoginService.isConfigured()) {
            log.warn("Google orqali kirish so'raldi, lekin GOOGLE_CLIENT_ID/SECRET sozlanmagan.");
            return redirectWithError(request, response,
                    "❌ Google orqali kirish hali sozlanmagan. Iltimos, boshqa usul bilan kiring.");
        }

        String state = googleLoginService.generateState();
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE_SESSION_KEY, state);

        return "redirect:" + googleLoginService.buildAuthorizationUrl(state);
    }

    @GetMapping("/oauth2/google/callback")
    public String callback(@RequestParam(required = false) String code,
                            @RequestParam(required = false) String state,
                            @RequestParam(required = false) String error,
                            @AuthenticationPrincipal(errorOnInvalidType = false) User currentUser,
                            HttpServletRequest request, HttpServletResponse response) {

        HttpSession session = request.getSession(false);
        String expectedState = session != null ? (String) session.getAttribute(STATE_SESSION_KEY) : null;
        if (session != null) session.removeAttribute(STATE_SESSION_KEY);

        if (error != null) {
            return redirectWithError(request, response, "Google orqali kirish bekor qilindi.");
        }
        if (code == null || expectedState == null || !expectedState.equals(state)) {
            log.warn("Google OAuth callback: state mos kelmadi yoki code yo'q.");
            return redirectWithError(request, response, "❌ Noto'g'ri so'rov. Qaytadan urinib ko'ring.");
        }

        User user;
        try {
            user = googleLoginService.handleCallback(code, currentUser);
        } catch (Exception e) {
            log.error("Google orqali kirishda xatolik", e);
            return redirectWithError(request, response, "❌ Google orqali kirishda xatolik yuz berdi.");
        }

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        log.info("Google orqali login: user={}", user.getUsername());
        return "redirect:/index";
    }

    private String redirectWithError(HttpServletRequest request, HttpServletResponse response, String message) {
        var flashMap = new FlashMap();
        flashMap.put("LOGIN_ERROR", message);
        new SessionFlashMapManager().saveOutputFlashMap(flashMap, request, response);
        return "redirect:/login";
    }
}
