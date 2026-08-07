package behzoddev.testproject.service;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.PageResponseDto;
import behzoddev.testproject.dto.profile.ChangeEmailDto;
import behzoddev.testproject.dto.profile.ChangePasswordDto;
import behzoddev.testproject.dto.profile.ChangePhoneDto;
import behzoddev.testproject.dto.profile.ChangeUsernameDto;
import behzoddev.testproject.dto.profile.TestHistoryDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.TestSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TestSessionRepository testSessionRepository;
    @Mock
    private TestSessionMapper testSessionMapper;
    @Mock
    private PhoneNumberService phoneNumberService;

    @InjectMocks
    private ProfileService profileService;

    // ===== changeUsername =====

    @Test
    void changeUsername_available_updatesUsername() {
        User user = User.builder().id(1L).username("old").build();
        when(userRepository.existsByUsername("new")).thenReturn(false);

        profileService.changeUsername(user, new ChangeUsernameDto("new"));

        assertThat(user.getUsername()).isEqualTo("new");
        verify(userRepository).save(user);
    }

    @Test
    void changeUsername_alreadyTaken_throwsConflict() {
        User user = User.builder().id(1L).username("old").build();
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> profileService.changeUsername(user, new ChangeUsernameDto("taken")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        assertThat(user.getUsername()).isEqualTo("old");
    }

    // ===== changeEmail =====

    @Test
    void changeEmail_newUniqueEmail_updates() {
        User user = User.builder().id(1L).email("old@mail.com").build();
        when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);

        profileService.changeEmail(user, new ChangeEmailDto("new@mail.com"));

        assertThat(user.getEmail()).isEqualTo("new@mail.com");
    }

    @Test
    void changeEmail_sameAsCurrentIgnoringCase_allowedWithoutUniquenessCheck() {
        User user = User.builder().id(1L).email("Same@Mail.com").build();

        profileService.changeEmail(user, new ChangeEmailDto("same@mail.com"));

        assertThat(user.getEmail()).isEqualTo("same@mail.com");
        verify(userRepository, never()).existsByEmail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changeEmail_blank_throwsBadRequest() {
        User user = User.builder().id(1L).email("old@mail.com").build();

        assertThatThrownBy(() -> profileService.changeEmail(user, new ChangeEmailDto("  ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void changeEmail_alreadyTakenByAnotherUser_throwsConflict() {
        User user = User.builder().id(1L).email("old@mail.com").build();
        when(userRepository.existsByEmail("taken@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> profileService.changeEmail(user, new ChangeEmailDto("taken@mail.com")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    // ===== changePhone =====

    @Test
    void changePhone_delegatesNormalizationToPhoneNumberService() {
        User user = User.builder().id(1L).build();
        when(phoneNumberService.normalize("UZ", "901234567")).thenReturn("+998901234567");

        profileService.changePhone(user, new ChangePhoneDto("UZ", "901234567"));

        assertThat(user.getPhoneNumber()).isEqualTo("+998901234567");
    }

    @Test
    void changePhone_invalidNumber_propagatesExceptionWithoutSaving() {
        User user = User.builder().id(1L).build();
        when(phoneNumberService.normalize("UZ", "1"))
                .thenThrow(new IllegalArgumentException("❌Telefon raqam noto'g'ri."));

        assertThatThrownBy(() -> profileService.changePhone(user, new ChangePhoneDto("UZ", "1")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(user);
    }

    // ===== changePassword =====

    @Test
    void changePassword_correctCurrentPassword_updatesToEncodedNewPassword() {
        User user = User.builder().id(1L).password("ENCODED_OLD").build();
        when(passwordEncoder.matches("old", "ENCODED_OLD")).thenReturn(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("ENCODED_NEW");

        profileService.changePassword(user, new ChangePasswordDto("old", "newpass1"));

        assertThat(user.getPassword()).isEqualTo("ENCODED_NEW");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsBadRequest() {
        User user = User.builder().id(1L).password("ENCODED_OLD").build();
        when(passwordEncoder.matches("wrong", "ENCODED_OLD")).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(user, new ChangePasswordDto("wrong", "newpass1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        assertThat(user.getPassword()).isEqualTo("ENCODED_OLD");
    }

    // ===== getHistory =====

    @Test
    void getHistory_wrapsPageResult() {
        User user = User.builder().id(1L).build();
        TestHistoryDto dto = new TestHistoryDto(1L, null, null, 10, 8, 2, 80, 120L);
        var page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
        when(testSessionRepository.getPageableTestHistoryDtoByUser(user, PageRequest.of(0, 10))).thenReturn(page);

        PageResponseDto<TestHistoryDto> result = profileService.getHistory(user, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.isFirst()).isTrue();
    }
}
