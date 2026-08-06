package behzoddev.testproject.service;

import behzoddev.testproject.dao.EmailVerificationCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.EmailVerificationCode;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Ro'yxatdan o'tishda email tasdiqlash — PasswordResetService bilan bir xil
 * g'oya (bir martalik kod, muddatli), lekin kanal doim email (ro'yxatdan
 * o'tishda email majburiy kiritiladi).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int CODE_TTL_MINUTES = 30;

    private final UserRepository userRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    // Ro'yxatdan o'tish tugagach darhol chaqiriladi — user obyekti allaqachon
    // qo'lda beriladi (qayta so'rov shart emas).
    @Transactional
    public void sendVerificationCode(User user) {
        String code = generateCode();

        EmailVerificationCode verificationCode = EmailVerificationCode.builder()
                .user(user)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                .build();

        codeRepository.save(verificationCode);
        emailService.sendVerificationCode(user.getEmail(), code);
    }

    @Transactional
    public String resend(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("❌Bunday foydalanuvchi topilmadi."));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("✅Email allaqachon tasdiqlangan. Kirish uchun login qiling.");
        }

        sendVerificationCode(user);
        return "✅Tasdiqlash kodi qayta yuborildi: " + maskEmail(user.getEmail());
    }

    @Transactional
    public void confirm(String username, String code) {
        EmailVerificationCode verificationCode = codeRepository
                .findByUser_UsernameAndCodeAndUsedFalseAndExpiresAtAfter(username, code, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("❌Kod noto'g'ri yoki muddati o'tgan."));

        User user = verificationCode.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationCode.setUsed(true);
        codeRepository.save(verificationCode);

        log.info("Email tasdiqlandi: {}", user.getUsername());
    }

    private String generateCode() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
