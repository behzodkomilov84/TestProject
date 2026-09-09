package behzoddev.testproject.config;

import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.OnlineUserTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Har bir autentifikatsiya qilingan so'rovda (sahifa, API — istalgani)
// foydalanuvchini OnlineUserTracker'da "hozir faol" deb belgilaydi.
// HandlerInterceptor (oddiy servlet Filter emas) ataylab tanlangan —
// DispatcherServlet handler'ga yetganda ishlaydi, shu payt Spring
// Security filter zanjiri ALLAQACHON SecurityContext'ni to'ldirgan
// bo'ladi (Filter sifatida ro'yxatdan o'tkazilsa, tartib SecurityConfig
// bilan aniq bog'lanishi kerak bo'lardi — bu yerda keraksiz).
@Component
@RequiredArgsConstructor
public class OnlineUserTrackingInterceptor implements HandlerInterceptor {

    private final OnlineUserTracker onlineUserTracker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            onlineUserTracker.touch(user.getId());
        }
        return true;
    }
}
