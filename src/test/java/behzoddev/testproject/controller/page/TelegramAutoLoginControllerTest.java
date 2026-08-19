package behzoddev.testproject.controller.page;

import behzoddev.testproject.dao.TelegramAutoLoginTokenRepository;
import behzoddev.testproject.entity.TelegramAutoLoginToken;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.TelegramBot;
import behzoddev.testproject.telegram.util.TokenHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * "Havola muddati tugagan bo'lsa, botda shuni xabarini bersin" —
 * foydalanuvchi eski (2 daqiqadan ortiq turgan) auto-login havolasini
 * bosganda, saytda jim /login'ga tashlab qo'yish o'rniga, botning
 * o'zida qaytadan urinib ko'rish haqida xabar yuboriladi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramAutoLoginControllerTest {

    @Mock
    private TelegramAutoLoginTokenRepository tokenRepository;
    @Mock
    private TelegramBot telegramBot;

    @Test
    void autoLogin_expiredToken_notifiesUserInBotAndRedirectsToLogin() throws Exception {
        TelegramAutoLoginController controller = new TelegramAutoLoginController(tokenRepository, telegramBot);

        User user = User.builder().id(1L).username("student").telegramId(777L).build();
        TelegramAutoLoginToken expired = TelegramAutoLoginToken.builder()
                .token(TokenHasher.sha256Hex("raw-token"))
                .user(user)
                .redirectPath("/courses")
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .used(false)
                .build();

        when(tokenRepository.findByTokenAndUsedFalse(TokenHasher.sha256Hex("raw-token")))
                .thenReturn(Optional.of(expired));

        String view = controller.autoLogin("raw-token", null, null);

        assertThat(view).isEqualTo("redirect:/login");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramBot).execute(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo("777");
        assertThat(captor.getValue().getText()).contains("Havola muddati tugagan");
    }

    @Test
    void autoLogin_unknownToken_doesNotNotifyAnyone() throws Exception {
        TelegramAutoLoginController controller = new TelegramAutoLoginController(tokenRepository, telegramBot);

        when(tokenRepository.findByTokenAndUsedFalse(any())).thenReturn(Optional.empty());

        String view = controller.autoLogin("garbage-token", null, null);

        assertThat(view).isEqualTo("redirect:/login");
        verify(telegramBot, never()).execute(any(SendMessage.class));
    }

    @Test
    void autoLogin_expiredToken_userWithoutTelegramId_doesNotThrowOrNotify() throws Exception {
        TelegramAutoLoginController controller = new TelegramAutoLoginController(tokenRepository, telegramBot);

        User user = User.builder().id(1L).username("student").telegramId(null).build();
        TelegramAutoLoginToken expired = TelegramAutoLoginToken.builder()
                .token(TokenHasher.sha256Hex("raw-token"))
                .user(user)
                .redirectPath("/courses")
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .used(false)
                .build();

        when(tokenRepository.findByTokenAndUsedFalse(any())).thenReturn(Optional.of(expired));

        String view = controller.autoLogin("raw-token", null, null);

        assertThat(view).isEqualTo("redirect:/login");
        verify(telegramBot, never()).execute(any(SendMessage.class));
    }
}
