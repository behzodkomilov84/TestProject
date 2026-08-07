package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.user.ChangeRoleDto;
import behzoddev.testproject.dto.user.LoginDto;
import behzoddev.testproject.dto.user.RegisterDto;
import behzoddev.testproject.dto.user.UserDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.exception.PasswordsDoNotMatchException;
import behzoddev.testproject.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RoleAuditService roleAuditService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private PhoneNumberService phoneNumberService;

    @InjectMocks
    private UserServiceImpl userService;

    private Role roleUser;
    private Role roleAdmin;

    @BeforeEach
    void setUp() {
        roleUser = Role.builder().id(1L).roleName("ROLE_USER").build();
        roleAdmin = Role.builder().id(2L).roleName("ROLE_ADMIN").build();
    }

    private RegisterDto registerDto(String phoneCountry, String phoneNumber) {
        return new RegisterDto("newuser", "new@mail.com", phoneCountry, phoneNumber, "secret1", "secret1");
    }

    // ===== register =====

    @Test
    void register_success_createsUserWithRoleUserAndSendsVerification() {
        RegisterDto dto = registerDto(null, null);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(roleUser));
        when(passwordEncoder.encode("secret1")).thenReturn("ENCODED");

        userService.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("newuser");
        assertThat(saved.getEmail()).isEqualTo("new@mail.com");
        assertThat(saved.getPassword()).isEqualTo("ENCODED");
        assertThat(saved.getPhoneNumber()).isNull();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getRoles()).containsExactly(roleUser);

        verify(emailVerificationService).sendVerificationCode(saved);
    }

    @Test
    void register_withValidPhone_normalizesAndStoresE164() {
        RegisterDto dto = registerDto("UZ", "901234567");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(roleUser));
        when(passwordEncoder.encode(anyString())).thenReturn("ENCODED");
        when(phoneNumberService.normalize("UZ", "901234567")).thenReturn("+998901234567");

        userService.register(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo("+998901234567");
    }

    @Test
    void register_invalidPhone_propagatesExceptionAndNeverSaves() {
        RegisterDto dto = registerDto("UZ", "123");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.of(roleUser));
        when(phoneNumberService.normalize("UZ", "123"))
                .thenThrow(new IllegalArgumentException("❌Telefon raqam noto'g'ri."));

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Telefon raqam");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_usernameAlreadyExists_throws() {
        RegisterDto dto = registerDto(null, null);
        when(userRepository.existsByUsername("newuser")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailBlank_throws() {
        RegisterDto dto = new RegisterDto("newuser", "  ", null, null, "secret1", "secret1");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email bo'sh");
    }

    @Test
    void register_emailAlreadyExists_throws() {
        RegisterDto dto = registerDto(null, null);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon ro'yxatdan o'tgan");
    }

    @Test
    void register_passwordMismatch_throws() {
        RegisterDto dto = new RegisterDto("newuser", "new@mail.com", null, null, "secret1", "different");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(PasswordsDoNotMatchException.class);
    }

    @Test
    void register_roleUserMissingInDatabase_throws() {
        RegisterDto dto = registerDto(null, null);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);
        when(roleRepository.findByRoleName("ROLE_USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ROLE_USER not found");
    }

    // ===== checkCredentials =====

    @Test
    void checkCredentials_success_doesNotThrow() {
        User user = User.builder().id(1L).username("bob").password("ENCODED").roles(new HashSet<>(Set.of(roleUser))).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("raw", "ENCODED")).thenReturn(true);

        userService.checkCredentials(new LoginDto("bob", "raw"));
        // no exception -> success
    }

    @Test
    void checkCredentials_wrongPassword_throws() {
        User user = User.builder().id(1L).username("bob").password("ENCODED").roles(new HashSet<>(Set.of(roleUser))).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        assertThatThrownBy(() -> userService.checkCredentials(new LoginDto("bob", "wrong")))
                .isInstanceOf(PasswordsDoNotMatchException.class);
    }

    @Test
    void checkCredentials_userNotFound_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.checkCredentials(new LoginDto("ghost", "raw")))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // ===== addRole / removeRole =====

    @Test
    void addRole_success_grantsRoleAndRecordsAudit() {
        User currentUser = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(roleAdmin))).build();
        User target = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        ChangeRoleDto result = userService.addRole(1L, "ROLE_ADMIN", auth);

        assertThat(target.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(result.roles()).contains("ROLE_ADMIN", "ROLE_USER");
        verify(roleAuditService).record(eq(target), eq(currentUser), eq("ROLE_ADMIN"),
                eq(behzoddev.testproject.entity.enums.RoleAuditAction.GRANTED),
                eq(behzoddev.testproject.entity.enums.RoleAuditSource.MANUAL));
    }

    @Test
    void addRole_selfChange_throwsAccessDenied() {
        User currentUser = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);

        assertThatThrownBy(() -> userService.addRole(1L, "ROLE_ADMIN", auth))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void addRole_targetNotFound_throws() {
        User currentUser = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(roleAdmin))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.addRole(1L, "ROLE_ADMIN", auth))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Foydalanuvchi topilmadi");
    }

    @Test
    void removeRole_success_revokesRoleAndRecordsAudit() {
        User currentUser = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(roleAdmin))).build();
        User target = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser, roleAdmin))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(roleAdmin));

        ChangeRoleDto result = userService.removeRole(1L, "ROLE_ADMIN", auth);

        assertThat(target.hasRole("ROLE_ADMIN")).isFalse();
        assertThat(result.roles()).containsExactly("ROLE_USER");
    }

    @Test
    void removeRole_selfChange_throwsAccessDenied() {
        User currentUser = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser, roleAdmin))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);

        assertThatThrownBy(() -> userService.removeRole(1L, "ROLE_ADMIN", auth))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removeRole_lastRemainingRole_throws() {
        User currentUser = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(roleAdmin))).build();
        User target = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.removeRole(1L, "ROLE_USER", auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kamida bitta roli");

        verify(userRepository, never()).save(any());
    }

    // ===== deleteUser =====

    @Test
    void deleteUser_success() {
        User currentUser = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(roleAdmin))).build();
        User target = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));

        UserDto result = userService.deleteUser(1L, auth);

        assertThat(result.username()).isEqualTo("bob");
        verify(userRepository).delete(target);
    }

    @Test
    void deleteUser_self_throwsAccessDenied() {
        User currentUser = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser))).build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(currentUser);

        assertThatThrownBy(() -> userService.deleteUser(1L, auth))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).delete(any());
    }

    // ===== unlockUser =====

    @Test
    void unlockUser_success_resetsLockoutAndNotifies() {
        User target = User.builder().id(1L).username("bob").roles(new HashSet<>(Set.of(roleUser)))
                .failedAttempts(5).lockedUntil(java.time.LocalDateTime.now().plusMinutes(10)).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));

        userService.unlockUser(1L);

        assertThat(target.getFailedAttempts()).isZero();
        assertThat(target.getLockedUntil()).isNull();
        verify(notificationService).create(eq(target), anyString(), org.mockito.ArgumentMatchers.isNull());
    }
}
