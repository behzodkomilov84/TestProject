package behzoddev.testproject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// application.yaml'da "telegram.bot.token: ${TELEGRAM_BOT_TOKEN}" — ataylab
// standart qiymatsiz (production'da soxta/bo'sh tokenga sezmasdan tushib
// qolmaslik uchun). Lokal/CI test muhitida bu muhit o'zgaruvchisi
// o'rnatilmagani uchun butun Spring konteksti yuklanolmay xato berardi.
// Haqiqiy token shart emas — TelegramConfig.tryRegister() token noto'g'ri
// bo'lsa ham xatoni tutib, jim ogohlantiradi va ilova ishga tushishda
// davom etadi (30 soniyada bir qayta urinadi), shuning uchun bu yerda
// faqat SOXTA (test uchun) qiymat berilsa kifoya — asosiy maqsad butun
// bean grafigi to'g'ri yig'ilishini tekshirish (haqiqiy Telegram bilan
// bog'lanish emas).
@SpringBootTest
@TestPropertySource(properties = "TELEGRAM_BOT_TOKEN=test-dummy-token-for-context-load-only")
class TestApplicationTests {

    @Test
    void contextLoads() {
    }

}
