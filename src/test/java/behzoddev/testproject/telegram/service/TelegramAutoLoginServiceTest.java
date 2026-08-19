package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.TelegramAutoLoginTokenRepository;
import behzoddev.testproject.entity.TelegramAutoLoginToken;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Botdagi "saytda ko'ring" havolalarini haqiqiy, avtomatik-login havolasiga
 * aylantiradi — bare "/courses" kabi matn Telegram tomonidan noma'lum bot
 * buyrug'i sifatida talqin qilinib, foydalanuvchiga chalkash xabar
 * ko'rsatardi (haqiqiy production bug, foydalanuvchi xabar bergan).
 */
@ExtendWith(MockitoExtension.class)
class TelegramAutoLoginServiceTest {

    @Mock
    private TelegramAutoLoginTokenRepository repository;

    private TelegramAutoLoginService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new TelegramAutoLoginService(repository);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://study-grow.uz");
        user = User.builder().id(1L).username("student").build();
    }

    @Test
    void buildLoginUrl_returnsUrlWithTokenQueryParam() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String url = service.buildLoginUrl(user, "/courses");

        assertThat(url).startsWith("https://study-grow.uz/telegram-auto-login?token=");
    }

    @Test
    void buildLoginUrl_savesTokenTiedToUserWithShortExpiry() {
        ArgumentCaptor<TelegramAutoLoginToken> captor = ArgumentCaptor.forClass(TelegramAutoLoginToken.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.buildLoginUrl(user, "/courses");

        TelegramAutoLoginToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getRedirectPath()).isEqualTo("/courses");
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getToken()).hasSizeGreaterThanOrEqualTo(32); // 256 bit, base64url
        // Qisqa muddatli — 5 daqiqadan oshmasligi kerak.
        assertThat(saved.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(5));
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ===== Baza faqat xeshni saqlaydi, xom tokenni emas =====

    @Test
    void buildLoginUrl_storesHashedToken_notRawTokenFromUrl() {
        ArgumentCaptor<TelegramAutoLoginToken> captor = ArgumentCaptor.forClass(TelegramAutoLoginToken.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        String url = service.buildLoginUrl(user, "/courses");
        String rawTokenFromUrl = url.substring(url.indexOf("token=") + "token=".length());

        String storedToken = captor.getValue().getToken();

        // Bazadagi qiymat URL'dagi xom token bilan bir xil bo'lmasligi kerak —
        // agar biror kishi bazani (masalan zaxira nusxasini) o'qisa ham,
        // undan haqiqiy login havolasini tiklab bo'lmasin.
        assertThat(storedToken).isNotEqualTo(rawTokenFromUrl);
        // Lekin xom tokenni xeshlasak, xuddi shu qiymat chiqishi kerak —
        // shu orqali TelegramAutoLoginController tekshiruvni bajara oladi.
        assertThat(storedToken).isEqualTo(TokenHasher.sha256Hex(rawTokenFromUrl));
    }

    @Test
    void buildLoginUrl_twoCalls_produceDifferentTokens() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String url1 = service.buildLoginUrl(user, "/courses");
        String url2 = service.buildLoginUrl(user, "/courses");

        assertThat(url1).isNotEqualTo(url2);
    }

    // ===== Ochiq-redirect zaifligidan himoya =====

    @Test
    void buildLoginUrl_externalRedirect_fallsBackToIndex() {
        ArgumentCaptor<TelegramAutoLoginToken> captor = ArgumentCaptor.forClass(TelegramAutoLoginToken.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.buildLoginUrl(user, "https://evil.com/phishing");

        assertThat(captor.getValue().getRedirectPath()).isEqualTo("/index");
    }

    @Test
    void buildLoginUrl_protocolRelativeRedirect_fallsBackToIndex() {
        ArgumentCaptor<TelegramAutoLoginToken> captor = ArgumentCaptor.forClass(TelegramAutoLoginToken.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.buildLoginUrl(user, "//evil.com/phishing");

        assertThat(captor.getValue().getRedirectPath()).isEqualTo("/index");
    }

    @Test
    void buildLoginUrl_nullRedirect_fallsBackToIndex() {
        ArgumentCaptor<TelegramAutoLoginToken> captor = ArgumentCaptor.forClass(TelegramAutoLoginToken.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.buildLoginUrl(user, null);

        assertThat(captor.getValue().getRedirectPath()).isEqualTo("/index");
    }

    @Test
    void buildLoginUrl_validLocalPath_keptAsIs() {
        ArgumentCaptor<TelegramAutoLoginToken> captor = ArgumentCaptor.forClass(TelegramAutoLoginToken.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.buildLoginUrl(user, "/teacher");

        assertThat(captor.getValue().getRedirectPath()).isEqualTo("/teacher");
    }
}
