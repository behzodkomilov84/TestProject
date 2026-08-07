package behzoddev.testproject.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Brute-force himoyasi: 5 marta ketma-ket noto'g'ri urinishdan keyin
 * hisobni 10 daqiqaga bloklash.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("bob").failedAttempts(0).build();
    }

    @Test
    void recordFailedAttempt_belowThreshold_incrementsWithoutLocking() {
        user.setFailedAttempts(2);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        loginAttemptService.recordFailedAttempt("bob");

        assertThat(user.getFailedAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void recordFailedAttempt_reachesFiveAttempts_locksForTenMinutes() {
        user.setFailedAttempts(4);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        loginAttemptService.recordFailedAttempt("bob");

        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(9));
        assertThat(user.getLockedUntil()).isBefore(LocalDateTime.now().plusMinutes(11));
    }

    @Test
    void recordFailedAttempt_previousLockExpired_resetsCounterFirst() {
        user.setFailedAttempts(5);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // muddati allaqachon o'tgan
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        loginAttemptService.recordFailedAttempt("bob");

        // 0'dan qayta boshlab +1 -> 1, hali bloklanmagan
        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void recordFailedAttempt_unknownUsername_doesNothing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        loginAttemptService.recordFailedAttempt("ghost");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetAttempts_clearsCounterAndLock() {
        user.setFailedAttempts(3);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        loginAttemptService.resetAttempts("bob");

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void resetAttempts_alreadyClean_doesNotSaveUnnecessarily() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        loginAttemptService.resetAttempts("bob");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void isLocked_futureLockedUntil_returnsTrue() {
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        assertThat(loginAttemptService.isLocked(user)).isTrue();
    }

    @Test
    void isLocked_pastLockedUntil_returnsFalse() {
        user.setLockedUntil(LocalDateTime.now().minusMinutes(5));
        assertThat(loginAttemptService.isLocked(user)).isFalse();
    }

    @Test
    void isLocked_noLock_returnsFalse() {
        assertThat(loginAttemptService.isLocked(user)).isFalse();
    }
}
