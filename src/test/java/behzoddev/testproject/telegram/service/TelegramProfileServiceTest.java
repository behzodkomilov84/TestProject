package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.profile.ChangeEmailDto;
import behzoddev.testproject.dto.profile.ChangePasswordDto;
import behzoddev.testproject.dto.profile.ChangePhoneDto;
import behzoddev.testproject.dto.profile.ChangeUsernameDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.PhoneNumberService;
import behzoddev.testproject.service.ProfileService;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
 * Botda "👤 Profil" — ko'p bosqichli tahrirlash oqimi (username/email/
 * telefon/parol). Haqiqiy o'zgartirish saytdagi bilan bir xil ProfileService
 * orqali bajariladi — bu yerda faqat bot-tomon marshrutlash/holat mantig'i
 * tekshiriladi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private PhoneNumberService phoneNumberService;
    @Mock
    private TelegramSessionService sessionService;

    @InjectMocks
    private TelegramProfileService telegramProfileService;

    private User user;
    private static final Long CHAT_ID = 555L;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).roleName("ROLE_USER").build();
        user = User.builder().id(1L).username("student").email("student@example.com")
                .telegramId(CHAT_ID).roles(new HashSet<>(Set.of(role))).build();
    }

    // ===== viewProfile =====

    @Test
    void viewProfile_userLinked_showsUsernameEmailPhoneRole() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        when(phoneNumberService.formatForDisplay(any())).thenReturn(null);

        SendMessage msg = telegramProfileService.viewProfile(CHAT_ID);

        assertThat(msg.getText()).contains("student").contains("student@example.com").contains("USER");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    // ===== startEdit* — holatni to'g'ri o'rnatadi =====

    @Test
    void startEditUsername_setsAwaitingUsernameState() {
        telegramProfileService.startEditUsername(CHAT_ID);

        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_USERNAME);
    }

    @Test
    void startEditPassword_setsAwaitingCurrentPasswordState() {
        telegramProfileService.startEditPassword(CHAT_ID);

        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_CURRENT_PASSWORD);
    }

    // ===== handleAwaitingInput: username =====

    @Test
    void handleAwaitingInput_username_success_clearsSessionAndConfirms() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));

        SendMessage result = telegramProfileService.handleAwaitingInput(CHAT_ID, BotState.AWAITING_USERNAME, "newname");

        verify(profileService).changeUsername(eq(user), eq(new ChangeUsernameDto("newname")));
        verify(sessionService).clear(CHAT_ID);
        assertThat(result.getText()).contains("✅").contains("newname");
    }

    @Test
    void handleAwaitingInput_username_alreadyTaken_promptsRetryWithoutClearingSession() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Band"))
                .when(profileService).changeUsername(any(), any());

        SendMessage result = telegramProfileService.handleAwaitingInput(CHAT_ID, BotState.AWAITING_USERNAME, "taken");

        verify(sessionService, never()).clear(CHAT_ID);
        assertThat(result.getText()).contains("❌").contains("Band");
    }

    // ===== handleAwaitingInput: email =====

    @Test
    void handleAwaitingInput_email_success_appliesChange() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));

        telegramProfileService.handleAwaitingInput(CHAT_ID, BotState.AWAITING_EMAIL, "new@example.com");

        verify(profileService).changeEmail(eq(user), eq(new ChangeEmailDto("new@example.com")));
        verify(sessionService).clear(CHAT_ID);
    }

    // ===== handleAwaitingInput: phone =====

    @Test
    void handleAwaitingInput_phone_success_normalizesWithUzDefault() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));

        telegramProfileService.handleAwaitingInput(CHAT_ID, BotState.AWAITING_PHONE, "901234567");

        verify(profileService).changePhone(eq(user), eq(new ChangePhoneDto("UZ", "901234567")));
        verify(sessionService).clear(CHAT_ID);
    }

    @Test
    void handleAwaitingInput_phone_invalid_doesNotClearSession() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        doThrow(new IllegalArgumentException("❌Telefon raqam noto'g'ri"))
                .when(profileService).changePhone(any(), any());

        SendMessage result = telegramProfileService.handleAwaitingInput(CHAT_ID, BotState.AWAITING_PHONE, "abc");

        verify(sessionService, never()).clear(CHAT_ID);
        assertThat(result.getText()).contains("❌");
    }

    // ===== handleAwaitingInput: parol (ikki bosqichli) =====

    @Test
    void handleAwaitingInput_currentPassword_movesToAwaitingNewPasswordAndStoresTemp() {
        SendMessage result = telegramProfileService.handleAwaitingInput(
                CHAT_ID, BotState.AWAITING_CURRENT_PASSWORD, "oldPass123");

        verify(sessionService).putTempData(CHAT_ID, "currentPassword", "oldPass123");
        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_NEW_PASSWORD);
        assertThat(result.getText()).contains("yangi parol");
    }

    @Test
    void handleAwaitingInput_newPassword_success_usesStoredCurrentPassword() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("currentPassword", "oldPass123"));

        SendMessage result = telegramProfileService.handleAwaitingInput(CHAT_ID, BotState.AWAITING_NEW_PASSWORD, "newPass456");

        verify(profileService).changePassword(eq(user), eq(new ChangePasswordDto("oldPass123", "newPass456")));
        verify(sessionService).clear(CHAT_ID);
        assertThat(result.getText()).contains("✅");
    }

    @Test
    void handleAwaitingInput_newPassword_wrongCurrentPassword_clearsSessionAndReportsError() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("currentPassword", "wrong"));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hozirgi parol noto'g'ri"))
                .when(profileService).changePassword(any(), any());

        SendMessage result = telegramProfileService.handleAwaitingInput(CHAT_ID, BotState.AWAITING_NEW_PASSWORD, "newPass456");

        verify(sessionService).clear(CHAT_ID);
        assertThat(result.getText()).contains("❌").contains("noto'g'ri");
    }

    // ===== cancelFlow =====

    @Test
    void cancelFlow_clearsSessionAndConfirms() {
        SendMessage result = telegramProfileService.cancelFlow(CHAT_ID);

        verify(sessionService).clear(CHAT_ID);
        assertThat(result.getText()).contains("Bekor qilindi");
    }
}
