package behzoddev.testproject.service;

import behzoddev.testproject.dao.PasswordResetCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.PasswordResetCode;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.PasswordResetChannel;
import behzoddev.testproject.exception.PasswordsDoNotMatchException;
import behzoddev.testproject.telegram.TelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parolni tiklash — Telegram ulangan bo'lsa ustuvor kanal, aks holda email.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetCodeRepository passwordResetCodeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private TelegramBot telegramBot;

    @InjectMocks
    private PasswordResetService passwordResetService;

    // ===== requestReset =====

    @Test
    void requestReset_userWithTelegram_sendsViaTelegramAndPrefersIt() throws Exception {
        User user = User.builder().id(1L).username("bob").telegramId(555L).email("bob@mail.com").build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        String result = passwordResetService.requestReset("bob");

        assertThat(result).contains("Telegram");
        ArgumentCaptor<PasswordResetCode> captor = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(passwordResetCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(PasswordResetChannel.TELEGRAM);
        verify(telegramBot).execute(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        verify(emailService, never()).sendPasswordResetCode(anyString(), anyString());
    }

    @Test
    void requestReset_telegramSendFails_stillReturnsSuccessMessage() throws Exception {
        // Telegram xatoligi yutiladi — parolni tiklash so'rovi umuman
        // muvaffaqiyatsiz bo'lib qolmasligi kerak.
        User user = User.builder().id(1L).username("bob").telegramId(555L).email("bob@mail.com").build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new RuntimeException("network")).when(telegramBot)
                .execute(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));

        String result = passwordResetService.requestReset("bob");

        assertThat(result).contains("Telegram");
    }

    @Test
    void requestReset_userWithoutTelegramButWithEmail_sendsViaEmail() throws Exception {
        User user = User.builder().id(1L).username("bob").telegramId(null).email("bob@mail.com").build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(emailService.sendPasswordResetCode(org.mockito.ArgumentMatchers.eq("bob@mail.com"), anyString()))
                .thenReturn(true);

        String result = passwordResetService.requestReset("bob");

        assertThat(result).contains("email");
        verify(telegramBot, never()).execute(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
    }

    @Test
    void requestReset_emailSendFails_returnsFailureMessageWithoutThrowing() {
        User user = User.builder().id(1L).username("bob").telegramId(null).email("bob@mail.com").build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(emailService.sendPasswordResetCode(anyString(), anyString())).thenReturn(false);

        String result = passwordResetService.requestReset("bob");

        assertThat(result).contains("xatolik");
    }

    @Test
    void requestReset_noTelegramAndNoEmail_throws() {
        User user = User.builder().id(1L).username("bob").telegramId(null).email(null).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordResetService.requestReset("bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Administrator bilan bog'laning");

        verify(passwordResetCodeRepository, never()).save(any());
    }

    @Test
    void requestReset_userNotFound_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.requestReset("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topilmadi");
    }

    // ===== confirmReset =====

    @Test
    void confirmReset_success_updatesPasswordAndMarksCodeUsed() {
        User user = User.builder().id(1L).username("bob").password("OLD").build();
        PasswordResetCode code = PasswordResetCode.builder().id(1L).user(user).code("123456")
                .channel(PasswordResetChannel.EMAIL).expiresAt(LocalDateTime.now().plusMinutes(5)).build();

        when(passwordResetCodeRepository.findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("bob"), org.mockito.ArgumentMatchers.eq("123456"), any()))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.encode("newpass1")).thenReturn("ENCODED");

        passwordResetService.confirmReset("bob", "123456", "newpass1", "newpass1");

        assertThat(user.getPassword()).isEqualTo("ENCODED");
        assertThat(code.isUsed()).isTrue();
    }

    @Test
    void confirmReset_passwordMismatch_throws() {
        assertThatThrownBy(() -> passwordResetService.confirmReset("bob", "123456", "aaa111", "bbb222"))
                .isInstanceOf(PasswordsDoNotMatchException.class);

        verify(passwordResetCodeRepository, never()).findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(
                anyString(), anyString(), any());
    }

    @Test
    void confirmReset_passwordTooShort_throws() {
        assertThatThrownBy(() -> passwordResetService.confirmReset("bob", "123456", "abc", "abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kamida 6 xonali");
    }

    @Test
    void confirmReset_invalidOrExpiredCode_throws() {
        when(passwordResetCodeRepository.findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(
                anyString(), anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.confirmReset("bob", "000000", "newpass1", "newpass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("muddati o'tgan");
    }
}
