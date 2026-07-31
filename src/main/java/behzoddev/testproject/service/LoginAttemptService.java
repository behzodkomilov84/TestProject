package behzoddev.testproject.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Brute-force himoyasi: 5 marta ketma-ket noto'g'ri urinishdan keyin
// hisobni 10 daqiqaga bloklaydi.
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 10;

    private final UserRepository userRepository;

    @Transactional
    public void recordFailedAttempt(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            // Avvalgi blok muddati tugagan bo'lsa, hisoblagichni yangidan boshlaymiz.
            if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            }

            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= MAX_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            }

            userRepository.save(user);
        });
    }

    @Transactional
    public void resetAttempts(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedAttempts() != 0 || user.getLockedUntil() != null) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }

    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }
}
