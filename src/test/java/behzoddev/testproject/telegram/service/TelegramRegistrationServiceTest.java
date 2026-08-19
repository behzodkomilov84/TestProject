package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.user.RegisterDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.exception.UserAlreadyExistsException;
import behzoddev.testproject.service.EmailVerificationService;
import behzoddev.testproject.service.PhoneNumberService;
import behzoddev.testproject.service.UserServiceImpl;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Botda to'g'ridan-to'g'ri ro'yxatdan o'tish — haqiqiy UserServiceImpl.register()/
 * EmailVerificationService orqali, saytdagi bilan bir xil validatsiya/oqim.
 */
@ExtendWith(MockitoExtension.class)
class TelegramRegistrationServiceTest {

    private static final Long CHAT_ID = 5000L;

    @Mock
    private UserServiceImpl userServiceImpl;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private PhoneNumberService phoneNumberService;
    @Mock
    private TelegramSessionService sessionService;
    @Mock
    private TelegramMenuService menuService;

    @InjectMocks
    private TelegramRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        lenient().when(menuService.welcomeText(any())).thenReturn("Xush kelibsiz!");
        lenient().when(menuService.buildMainMenu(any())).thenReturn(null);
    }

    @Test
    void start_setsAwaitingUsernameState() {
        registrationService.start(CHAT_ID);

        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_USERNAME);
    }

    // ===== Username =====

    @Test
    void applyUsername_tooShort_retriesWithoutAdvancing() {
        SendMessage msg = registrationService.applyUsername(CHAT_ID, "ab");

        assertThat(msg.getText()).contains("kamida 3");
        verify(sessionService, never()).setState(eq(CHAT_ID), eq(BotState.AWAITING_REG_EMAIL));
    }

    @Test
    void applyUsername_taken_retries() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        SendMessage msg = registrationService.applyUsername(CHAT_ID, "taken");

        assertThat(msg.getText()).contains("band");
    }

    @Test
    void applyUsername_valid_movesToEmailStep() {
        when(userRepository.existsByUsername("newstudent")).thenReturn(false);

        SendMessage msg = registrationService.applyUsername(CHAT_ID, "newstudent");

        verify(sessionService).putTempData(CHAT_ID, "reg_username", "newstudent");
        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_EMAIL);
        assertThat(msg.getText()).contains("email");
    }

    // ===== Email =====

    @Test
    void applyEmail_invalidFormat_retries() {
        SendMessage msg = registrationService.applyEmail(CHAT_ID, "not-an-email");

        assertThat(msg.getText()).contains("To'g'ri email");
    }

    @Test
    void applyEmail_alreadyRegistered_retries() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        SendMessage msg = registrationService.applyEmail(CHAT_ID, "taken@example.com");

        assertThat(msg.getText()).contains("allaqachon ro'yxatdan o'tgan");
    }

    @Test
    void applyEmail_valid_movesToPhoneStepWithSkipButton() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        SendMessage msg = registrationService.applyEmail(CHAT_ID, "new@example.com");

        verify(sessionService).putTempData(CHAT_ID, "reg_email", "new@example.com");
        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_PHONE);
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    // ===== Telefon =====

    @Test
    void applyPhone_valid_normalizesAndMovesToPassword() {
        when(phoneNumberService.normalize("UZ", "901234567")).thenReturn("+998901234567");

        registrationService.applyPhone(CHAT_ID, "901234567");

        verify(sessionService).putTempData(CHAT_ID, "reg_phone", "+998901234567");
        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_PASSWORD);
    }

    @Test
    void applyPhone_invalid_retriesWithoutAdvancing() {
        when(phoneNumberService.normalize("UZ", "abc")).thenThrow(new IllegalArgumentException("❌Telefon raqam noto'g'ri."));

        SendMessage msg = registrationService.applyPhone(CHAT_ID, "abc");

        assertThat(msg.getText()).contains("noto'g'ri");
        verify(sessionService, never()).setState(eq(CHAT_ID), eq(BotState.AWAITING_REG_PASSWORD));
    }

    @Test
    void skipPhone_movesToPasswordWithoutStoringPhone() {
        registrationService.skipPhone(CHAT_ID);

        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_PASSWORD);
        verify(sessionService, never()).putTempData(eq(CHAT_ID), eq("reg_phone"), any());
    }

    // ===== Parol =====

    @Test
    void applyPassword_tooShort_retries() {
        SendMessage msg = registrationService.applyPassword(CHAT_ID, "123");

        assertThat(msg.getText()).contains("kamida 6");
    }

    @Test
    void applyPassword_valid_movesToConfirmStep() {
        registrationService.applyPassword(CHAT_ID, "secret1");

        verify(sessionService).putTempData(CHAT_ID, "reg_password", "secret1");
        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_CONFIRM_PASSWORD);
    }

    @Test
    void applyConfirmPassword_mismatch_retries() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("reg_password", "secret1"));

        SendMessage msg = registrationService.applyConfirmPassword(CHAT_ID, "different");

        assertThat(msg.getText()).contains("mos kelmadi");
        verify(sessionService, never()).setState(eq(CHAT_ID), eq(BotState.AWAITING_REG_TERMS));
    }

    @Test
    void applyConfirmPassword_match_showsTermsWithButtons() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("reg_password", "secret1"));

        SendMessage msg = registrationService.applyConfirmPassword(CHAT_ID, "secret1");

        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_TERMS);
        assertThat(msg.getText()).contains("Foydalanish shartlari");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    // ===== Shartlarga rozilik -> ro'yxatdan o'tish =====

    @Test
    void confirmTerms_success_registersAndLinksTelegramIdThenAsksForEmailCode() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of(
                "reg_username", "newstudent", "reg_email", "new@example.com", "reg_password", "secret1"));

        User created = User.builder().id(10L).username("newstudent").build();
        when(userRepository.findByUsername("newstudent")).thenReturn(Optional.of(created));

        SendMessage msg = registrationService.confirmTerms(CHAT_ID);

        verify(userServiceImpl).register(any(RegisterDto.class));
        assertThat(created.getTelegramId()).isEqualTo(CHAT_ID);
        verify(userRepository).save(created);
        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_REG_EMAIL_CODE);
        assertThat(msg.getText()).contains("Ro'yxatdan o'tdingiz");
    }

    @Test
    void confirmTerms_usernameTaken_clearsSessionAndReportsError() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of(
                "reg_username", "taken", "reg_email", "new@example.com", "reg_password", "secret1"));
        doThrow(new UserAlreadyExistsException("taken")).when(userServiceImpl).register(any());

        SendMessage msg = registrationService.confirmTerms(CHAT_ID);

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("❌");
        verify(userRepository, never()).save(any());
    }

    @Test
    void cancelTerms_clearsSession() {
        SendMessage msg = registrationService.cancelTerms(CHAT_ID);

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("bekor qilindi");
    }

    // ===== Email tasdiqlash kodi =====

    @Test
    void applyEmailCode_valid_clearsSessionAndShowsMainMenu() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("reg_username", "newstudent"));
        User user = User.builder().id(10L).username("newstudent").roles(new HashSet<>(Set.of())).build();
        when(userRepository.findByUsername("newstudent")).thenReturn(Optional.of(user));

        SendMessage msg = registrationService.applyEmailCode(CHAT_ID, "123456");

        verify(emailVerificationService).confirm("newstudent", "123456");
        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("tasdiqlandi");
    }

    @Test
    void applyEmailCode_invalid_retriesWithoutClearingSession() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("reg_username", "newstudent"));
        doThrow(new IllegalArgumentException("❌Kod noto'g'ri yoki muddati o'tgan."))
                .when(emailVerificationService).confirm("newstudent", "000000");

        SendMessage msg = registrationService.applyEmailCode(CHAT_ID, "000000");

        verify(sessionService, never()).clear(CHAT_ID);
        assertThat(msg.getText()).contains("noto'g'ri");
    }

    @Test
    void resendCode_success_confirmsSent() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("reg_username", "newstudent"));
        when(emailVerificationService.resend("newstudent")).thenReturn("✅Tasdiqlash kodi qayta yuborildi: n***@example.com");

        SendMessage msg = registrationService.resendCode(CHAT_ID);

        assertThat(msg.getText()).contains("qayta yuborildi");
    }

    // ===== Bekor qilish =====

    @Test
    void cancelFlow_clearsSession() {
        SendMessage msg = registrationService.cancelFlow(CHAT_ID);

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("Bekor qilindi");
    }
}
