package behzoddev.testproject.security;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// HAQIQIY TOPILGAN KAMCHILIK (foydalanuvchi so'rovi, 2026-09-10): Spring
// Security foydalanuvchi rollarini LOGIN paytida sessiyaga "muzlatib"
// saqlaydi (User principal — o'zgarmas obyekt sifatida). Kurs obunasidan
// farqli (CourseService.isSubscribed — HAR so'rovda DB'dan real vaqtda
// tekshiriladi), ROLE_ADMIN'ga bog'liq cheklovlar (SecurityConfig'dagi
// ".hasAnyAuthority(...)", @PreAuthorize) ESKI (login paytidagi)
// ro'yxatga qaraydi. Natija: obuna muddati tugab, kunlik job DB'da
// ROLE_ADMIN'ni olib tashlasa ham — foydalanuvchi HALI CHIQMAGAN
// (faol) sessiyasida ADMIN huquqlari muddat tugagandan keyin ham
// ishlayveradi, toki u chiqib qayta kirmaguncha.
//
// BU FILTER shu bo'shliqni yopadi: har so'rovda (throttled — ko'pi
// bilan CHECK_THROTTLE_SECONDS'da bir marta) foydalanuvchining DB'dagi
// ASL rollarini sessiyadagi (eski) ro'yxat bilan solishtiradi; farq
// bo'lsa — SecurityContext'ni YANGI (DB'dan yangi o'qilgan) Authentication
// bilan almashtiradi va SESSIYAGA DARHOL saqlaydi (keyingi so'rovda ham
// yangi holat qolishi uchun).
//
// MUHIM: bu servlet Filter sifatida (HandlerInterceptor EMAS) yozilgan
// va SecurityConfig'da AuthorizationFilter'dan OLDIN ro'yxatdan
// o'tkazilishi SHART — SecurityConfig'dagi ".hasAnyAuthority(...)" kabi
// URL darajasidagi tekshiruvlar AuthorizationFilter'da amalga oshadi,
// bu esa DispatcherServlet'dan (demak har qanday HandlerInterceptor'dan
// ham) OLDINROQ ishlaydi — shuning uchun HandlerInterceptor bilan bu
// muammoni yopib bo'lmas edi.
@Slf4j
@Component
@RequiredArgsConstructor
public class FreshAuthoritiesFilter extends OncePerRequestFilter {

    private static final long CHECK_THROTTLE_SECONDS = 30;

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    private final Map<Long, Instant> lastCheckedByUserId = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User sessionUser
                && sessionUser.getId() != null) {

            Instant now = Instant.now();
            Instant lastChecked = lastCheckedByUserId.get(sessionUser.getId());
            if (lastChecked == null || lastChecked.isBefore(now.minusSeconds(CHECK_THROTTLE_SECONDS))) {
                lastCheckedByUserId.put(sessionUser.getId(), now);
                refreshIfChanged(request, response, auth, sessionUser);
            }
        }

        chain.doFilter(request, response);
    }

    private void refreshIfChanged(HttpServletRequest request, HttpServletResponse response,
                                   Authentication auth, User sessionUser) {
        User freshUser = userRepository.findById(sessionUser.getId()).orElse(null);

        // Foydalanuvchi o'chirilgan (masalan boshqa OWNER tomonidan) —
        // faol sessiya darhol tugatiladi, aks holda o'chirilgan hisob
        // sessiyasi tabiiy tugaguncha "faol" bo'lib qolaverardi.
        if (freshUser == null) {
            SecurityContextHolder.clearContext();
            securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
            log.info("Sessiyadagi foydalanuvchi endi DB'da mavjud emas — sessiya tugatildi: id={}", sessionUser.getId());
            return;
        }

        Set<String> sessionRoles = authorityNames(sessionUser.getAuthorities());
        Set<String> freshRoles = authorityNames(freshUser.getAuthorities());

        if (sessionRoles.equals(freshRoles)) return;

        UsernamePasswordAuthenticationToken newAuth =
                new UsernamePasswordAuthenticationToken(freshUser, auth.getCredentials(), freshUser.getAuthorities());
        newAuth.setDetails(auth.getDetails());

        SecurityContext newContext = SecurityContextHolder.createEmptyContext();
        newContext.setAuthentication(newAuth);
        SecurityContextHolder.setContext(newContext);
        securityContextRepository.saveContext(newContext, request, response);

        log.info("Foydalanuvchi rollari sessiyada DB bilan sinxronlandi: user={}, eski={}, yangi={}",
                freshUser.getUsername(), sessionRoles, freshRoles);
    }

    private Set<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
