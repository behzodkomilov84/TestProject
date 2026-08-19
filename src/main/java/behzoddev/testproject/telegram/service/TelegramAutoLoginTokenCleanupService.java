package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.TelegramAutoLoginTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// telegram_auto_login_tokens jadvali cheksiz o'sib ketmasligi uchun —
// tokenlar ishlatilgandan/muddati o'tgandan keyin ham o'zi o'chib
// ketmaydi (TelegramAutoLoginController faqat "used=true" deb belgilaydi,
// muddati o'tganini esa umuman o'zgartirmaydi). 1 kunlik "grace period"
// qoldiriladi — shu muddat ichida TelegramAutoLoginController hali ham
// "muddati o'tgan" tokenni topib, foydalanuvchiga botda xabar bera oladi
// (notifyExpiredToken). 1 kundan keyin esa endi hech kimga kerak emas.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAutoLoginTokenCleanupService {

    private static final int RETENTION_DAYS = 1;

    private final TelegramAutoLoginTokenRepository repository;

    @Scheduled(cron = "0 40 0 * * *") // har kuni 00:40'da
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        long deleted = repository.deleteByExpiresAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Eski Telegram auto-login tokenlari tozalandi: {} ta", deleted);
        }
    }
}
