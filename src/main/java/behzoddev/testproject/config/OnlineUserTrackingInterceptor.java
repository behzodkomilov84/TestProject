package behzoddev.testproject.config;

import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.OnlineUserTracker;
import behzoddev.testproject.service.UserActivityTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Har bir autentifikatsiya qilingan so'rovda (sahifa, API — istalgani)
// foydalanuvchini OnlineUserTracker'da "hozir faol" deb belgilaydi VA
// UserActivityTracker'ga "saytda o'tkazgan vaqt" hisobini yuritish
// uchun beradi (foydalanuvchi so'rovi, 2026-09-10). HandlerInterceptor
// (oddiy servlet Filter emas) ataylab tanlangan — DispatcherServlet
// handler'ga yetganda ishlaydi, shu payt Spring Security filter zanjiri
// ALLAQACHON SecurityContext'ni to'ldirgan bo'ladi (Filter sifatida
// ro'yxatdan o'tkazilsa, tartib SecurityConfig bilan aniq bog'lanishi
// kerak bo'lardi — bu yerda keraksiz).
@Component
@RequiredArgsConstructor
public class OnlineUserTrackingInterceptor implements HandlerInterceptor {

    private final OnlineUserTracker onlineUserTracker;
    private final UserActivityTracker userActivityTracker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            onlineUserTracker.touch(user.getId());
            userActivityTracker.track(user.getId(), request.getRequestURI());
        }
        return true;
    }
}
