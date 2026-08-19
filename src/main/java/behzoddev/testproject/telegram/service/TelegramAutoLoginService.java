package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.TelegramAutoLoginTokenRepository;
import behzoddev.testproject.entity.TelegramAutoLoginToken;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

// Botdagi "saytda ko'ring" havolalarini haqiqiy, bosilganda avtomatik
// (parolsiz) login qiladigan manzilga aylantiradi. Bare "/courses" kabi
// matn Telegram tomonidan noma'lum bot buyrug'i sifatida talqin qilinib,
// "Noma'lum buyruq" xabariga olib kelardi — endi haqiqiy HTTPS havola.
@Service
@RequiredArgsConstructor
public class TelegramAutoLoginService {

    private static final int TOKEN_VALID_MINUTES = 2;
    private static final int TOKEN_BYTES = 32; // 256 bit — taxmin qilib bo'lmaydi

    private final TelegramAutoLoginTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Transactional
    public String buildLoginUrl(User user, String redirectPath) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // Bazaga xom token emas, uning xeshi yoziladi (TokenHasher) — asl
        // token faqat quyida foydalanuvchiga qaytariladigan URL'da qoladi.
        TelegramAutoLoginToken entity = TelegramAutoLoginToken.builder()
                .token(TokenHasher.sha256Hex(token))
                .user(user)
                .redirectPath(sanitizeRedirect(redirectPath))
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_VALID_MINUTES))
                .used(false)
                .build();

        repository.save(entity);

        return publicBaseUrl + "/telegram-auto-login?token=" + token;
    }

    // Faqat saytning o'z ichki (nisbiy) yo'llariga — ochiq-redirect
    // zaifligining oldini olish uchun ("//evil.com" kabi holatlar ham).
    private String sanitizeRedirect(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            return "/index";
        }
        return path;
    }
}
