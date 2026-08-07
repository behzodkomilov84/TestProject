package behzoddev.testproject.service;

import behzoddev.testproject.dao.EmailVerificationCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.EmailVerificationCode;
import behzoddev.testproject.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationCodeRepository codeRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    // ===== sendVerificationCode =====

    @Test
    void sendVerificationCode_savesCodeAndEmailsUser() {
        User user = User.builder().id(1L).username("bob").email("bob@mail.com").build();

        emailVerificationService.sendVerificationCode(user);

        ArgumentCaptor<EmailVerificationCode> captor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(codeRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getCode()).matches("\\d{6}");
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));

        verify(emailService).sendVerificationCode(eq("bob@mail.com"), anyString());
    }

    // ===== resend =====

    @Test
    void resend_unverifiedUser_sendsNewCode() {
        User user = User.builder().id(1L).username("bob").email("bob@mail.com").emailVerified(false).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        String result = emailVerificationService.resend("bob");

        assertThat(result).contains("qayta yuborildi");
        verify(codeRepository).save(any());
    }

    @Test
    void resend_alreadyVerified_throws() {
        User user = User.builder().id(1L).username("bob").email("bob@mail.com").emailVerified(true).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> emailVerificationService.resend("bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon tasdiqlangan");

        verify(codeRepository, never()).save(any());
    }

    @Test
    void resend_userNotFound_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.resend("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topilmadi");
    }

    @Test
    void resend_masksEmailInReturnedMessage() {
        User user = User.builder().id(1L).username("bob").email("bob@mail.com").emailVerified(false).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        String result = emailVerificationService.resend("bob");

        assertThat(result).contains("b***@mail.com");
        assertThat(result).doesNotContain("bob@mail.com");
    }

    // ===== confirm =====

    @Test
    void confirm_validCode_marksUserVerifiedAndCodeUsed() {
        User user = User.builder().id(1L).username("bob").emailVerified(false).build();
        EmailVerificationCode code = EmailVerificationCode.builder().id(1L).user(user).code("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();

        when(codeRepository.findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(
                eq("bob"), eq("123456"), any())).thenReturn(Optional.of(code));

        emailVerificationService.confirm("bob", "123456");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(code.isUsed()).isTrue();
    }

    @Test
    void confirm_invalidOrExpiredCode_throws() {
        when(codeRepository.findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(
                anyString(), anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.confirm("bob", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("muddati o'tgan");
    }
}
