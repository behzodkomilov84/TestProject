package behzoddev.testproject.controller.page;

import behzoddev.testproject.dao.TelegramAutoLoginTokenRepository;
import behzoddev.testproject.entity.TelegramAutoLoginToken;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.TelegramBot;
import behzoddev.testproject.telegram.util.TokenHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;

// Telegram botdan yuborilgan "saytda ko'ring" havolasi shu yerga tushadi —
// bitta martalik tokenni tekshirib, foydalanuvchini avtomatik (parolsiz)
// login qildiradi va so'ralgan sahifaga yo'naltiradi. Token allaqachon
// /link orqali ulangan (tasdiqlangan) akkauntlar uchungina beriladi
// (TelegramAutoLoginService), shuning uchun bu — qo'shimcha autentifikatsiya
// usuli emas, balki foydalanuvchi allaqachon isbotlagan shaxsini
// (Telegram akkaunti orqali) saytga o'tkazish vositasi.
@Slf4j
@Controller
@RequiredArgsConstructor
public class TelegramAutoLoginController {

    private final TelegramAutoLoginTokenRepository tokenRepository;
    private final TelegramBot telegramBot;

    @GetMapping("/telegram-auto-login")
    @Transactional
    public String autoLogin(@RequestParam String token, HttpServletRequest request, HttpServletResponse response) {

        // Bazada tokenning o'zi emas, xeshi saqlanadi — solishtirish uchun
        // kelgan xom tokenni ham xeshlaymiz (TelegramAutoLoginService bilan
        // bir xil algoritm).
        TelegramAutoLoginToken entity = tokenRepository
                .findByTokenAndUsedFalse(TokenHasher.sha256Hex(token))
                .orElse(null);

        if (entity == null) {
            // Token noma'lum (allaqachon ishlatilgan yoki hech qachon
            // bo'lmagan) — kimga tegishli ekanini bilmaymiz, botga
            // xabar yubora olmaymiz.
            log.warn("Telegram avtomatik login: token yaroqsiz (noma'lum yoki allaqachon ishlatilgan)");
            return "redirect:/login";
        }

        if (entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Muddati o'tgan (lekin kim ekani ma'lum) — foydalanuvchiga
            // botning o'zida xabar beramiz, qaytadan urinib ko'rishi uchun.
            log.warn("Telegram avtomatik login: token muddati o'tgan, user={}", entity.getUser().getUsername());
            notifyExpiredToken(entity.getUser());
            return "redirect:/login";
        }

        // Bitta martalik — darhol "ishlatilgan" deb belgilaymiz (havola
        // qayta bosilsa yoki kimdir uni ushlab qolsa, ikkinchi marta
        // ishlamasligi uchun).
        entity.setUsed(true);
        tokenRepository.save(entity);

        User user = entity.getUser();

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        log.info("Telegram orqali avtomatik login: user={}", user.getUsername());

        String redirect = entity.getRedirectPath() != null ? entity.getRedirectPath() : "/index";
        return "redirect:" + redirect;
    }

    // Havola muddati (2 daqiqa) o'tib ketgan bo'lsa, foydalanuvchi bunga
    // duch kelganini sezmasligi mumkin (masalan Telegram xabari eski
    // bo'lsa) — shuning uchun botning o'zida qaytadan urinib ko'rish
    // haqida aniq xabar yuboramiz.
    private void notifyExpiredToken(User user) {
        if (user.getTelegramId() == null) return;

        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());
        msg.setText("⏰ Havola muddati tugagan (2 daqiqadan ortiq turgan edi). " +
                "Iltimos, botda kerakli menyu tugmasini qaytadan bosib, yangi havola oling.");

        try {
            telegramBot.execute(msg);
        } catch (TelegramApiException e) {
            log.error("Muddati o'tgan token haqida Telegram xabarini yuborishda xatolik", e);
        }
    }
}
