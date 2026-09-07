package behzoddev.testproject.telegram.controller;

import behzoddev.testproject.security.SecurityUtils;
import behzoddev.testproject.telegram.service.TelegramLinkCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TelegramLinkCodeController {

    private final TelegramLinkCodeService telegramLinkCodeService;

    // Bot nomini frontend'ga qaytarish uchun — navbar.js botga TO'G'RIDAN-
    // TO'G'RI o'tish havolasini ("https://t.me/<username>?start=link_<kod>")
    // shu yerdan quradi (foydalanuvchi so'rovi, 2026-09-08: "botni nomini
    // bilmaydigan foydalanuvchi nima qilishni bilmaydi... bu sahifadan
    // botga to'g'ridan-to'g'ri o'tib ketsin").
    @Value("${telegram.bot.username}")
    private String botUsername;

    @PostMapping("/api/telegram/link")
    public Map<String, String> createLink() {

        Long userId = SecurityUtils.getCurrentUserId();

        String code = telegramLinkCodeService.generateCode(userId);

        return Map.of("code", code, "botUsername", botUsername);
    }

}
