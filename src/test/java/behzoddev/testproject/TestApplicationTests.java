package behzoddev.testproject;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.testsession.UserTestSessionStatsDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.TestPropertySource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }

    // "📊 Statistika" -> "👤 Foydalanuvchilar kesimida test statistikasi"
    // (foydalanuvchi so'rovi, 2026-09-13) — Mockito bilan test qilib
    // bo'lmaydi (repository o'zi mocklanadi, JPQL sintaksisi tekshirilmay
    // qoladi). Bu yerda HAQIQIY Hibernate/JPQL dvigateli, HAQIQIY lokal
    // MySQL'ga qarshi ishga tushiriladi — "left join TestSession t on
    // t.user = u" (bog'lanmagan/ad-hoc entity join), agregat
    // funksiyalar (count/sum/avg/max) va DTO konstruktor ifodasidagi
    // TUR MOSLIGI (masalan avg() Double qaytaradi, sum(Integer) esa
    // Long) — bularning har biri JPQL sintaksis xatosi yoki
    // "NoSuchMethodException" (konstruktorga mos kelmagan tur) tarzida
    // FAQAT ishga tushirilganda ko'rinadi, compile vaqtida emas.
    @Test
    void findAllUserTestSessionStats_runsAgainstRealDatabaseWithoutError() {
        List<UserTestSessionStatsDto> stats = userRepository.findAllUserTestSessionStats();

        assertThat(stats).isNotNull();
        stats.forEach(row -> {
            assertThat(row.userId()).isNotNull();
            assertThat(row.username()).isNotNull();
            assertThat(row.sessionCount()).isNotNull().isGreaterThanOrEqualTo(0L);
            assertThat(row.avgPercent()).isNotNull();
        });
    }

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "guruhlash
    // + checkbox qo'shildi debsan, lekin frontda ko'rinmayapti") — sababi
    // brauzer keshi edi (config/AppBuildInfo.java'ga qarang). Tuzatish
    // sifatida courseDetail.html/coursesCatalog.html'ga qo'shilgan
    // "th:href/th:src ... ?v=${T(...).STARTED_AT}" Thymeleaf ifodasi
    // TO'G'RI (xatosiz) ekanini shu HAQIQIY ishlatilgan Thymeleaf
    // dvigateli (Spring Boot avtokonfiguratsiya qilgan bean, ishlab
    // chiqarishdagi bilan BIR XIL dialektlar) orqali tasdiqlaydi —
    // noto'g'ri SpringEL sintaksisi (masalan xato yozilgan sinf nomi)
    // production'da 500 xatosiga olib kelgan bo'lardi. "cacheBustingProbe"
    // (src/test/resources/templates) — FAQAT shu ikki atributning o'zini
    // (courseDetail.html/coursesCatalog.html'dagi bilan AYNAN BIR XIL)
    // izolyatsiya qilingan holda tekshiradi — asosiy sahifalarning o'zini
    // emas, chunki ular navbar fragment orqali Spring Security'ning
    // Thymeleaf dialektiga bog'liq (buning uchun to'liq web-server/xavfsizlik
    // muhiti kerak bo'lardi, bu yerda ATAYLAB kerak emas).
    @Test
    void cacheBustedAssetExpression_evaluatesToNumericTimestampWithoutError() {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(application.buildExchange(request, response), Locale.forLanguageTag("uz"));

        String html = templateEngine.process("cacheBustingProbe", context);

        assertThat(html).containsPattern("/css/courses\\.css\\?v=\\d+");
        assertThat(html).containsPattern("/js/courseDetail\\.js\\?v=\\d+");
    }

}
