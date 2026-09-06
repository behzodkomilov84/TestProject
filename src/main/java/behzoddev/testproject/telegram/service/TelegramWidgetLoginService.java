package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// "Telegram orqali kirish" — Telegram Login Widget
// (https://core.telegram.org/widgets/login) orqali, alohida OAuth ilova
// ro'yxatdan o'tkazmasdan (faqat @BotFather'da "/setdomain" bilan sayt
// domeni ruxsat etiladi) login/ro'yxatdan o'tish (foydalanuvchi so'rovi,
// 2026-09-06: "Facebook, google, telegram орқали кириш" — uchtasidan
// birinchi navbatda Telegram tanlandi, chunki bot infratuzilmasi
// allaqachon bor). Telegram foydalanuvchi ma'lumotlarini (id, first_name,
// ...) HAMMA UCHUN OCHIQ redirect orqali yuboradi, shuning uchun HAR BIR
// so'rovni HMAC-SHA256 imzosi (bot tokeni orqali) bilan tekshirish SHART —
// aks holda istalgan kishi o'zini istalgan Telegram foydalanuvchisi
// sifatida ko'rsatib, login qila olardi.
@Service
@RequiredArgsConstructor
public class TelegramWidgetLoginService {

    // Telegram'ning rasmiy tavsiyasi — imzoning o'zi abadiy amal qiladi
    // (auth_date'ga bog'liq emas), lekin havola URL'da (server log'lari,
    // brauzer tarixi) uzoq umr ko'rmasligi uchun eskirgan so'rovlar rad
    // etiladi.
    private static final long MAX_AUTH_AGE_SECONDS = 24 * 60 * 60;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    @Value("${telegram.bot.token}")
    private String botToken;

    public static class InvalidTelegramAuthException extends RuntimeException {
        public InvalidTelegramAuthException(String message) {
            super(message);
        }
    }

    @Transactional
    public User resolveUser(Map<String, String> params) {
        if (!verifySignature(params)) {
            throw new InvalidTelegramAuthException("❌ Telegram imzosi noto'g'ri — bu so'rov soxta bo'lishi mumkin.");
        }

        long authDate = Long.parseLong(params.getOrDefault("auth_date", "0"));
        if (Instant.now().getEpochSecond() - authDate > MAX_AUTH_AGE_SECONDS) {
            throw new InvalidTelegramAuthException("⏰ Havola muddati tugagan, qaytadan urinib ko'ring.");
        }

        long telegramId = Long.parseLong(params.get("id"));

        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> createUser(telegramId, params.get("username")));
    }

    // Telegram'ning rasmiy tekshirish algoritmi: "hash"dan boshqa barcha
    // maydonlar "key=value" ko'rinishida, kalit bo'yicha alifbo tartibida
    // saralanib "\n" bilan birlashtiriladi; bot tokenining SHA-256 xeshi
    // HMAC kaliti sifatida ishlatiladi.
    private boolean verifySignature(Map<String, String> params) {
        String hash = params.get("hash");
        if (hash == null || hash.isBlank()) {
            return false;
        }

        String dataCheckString = params.entrySet().stream()
                .filter(e -> !e.getKey().equals("hash"))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));

        try {
            byte[] secretKey = MessageDigest.getInstance("SHA-256").digest(botToken.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] computed = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);

            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    hash.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Telegram imzosini tekshirishda kutilmagan xatolik", e);
        }
    }

    // Birinchi marta Telegram orqali kirgan foydalanuvchi uchun — parol
    // yoki email so'ralmaydi (Telegram identifikatsiyaning o'zi yetarli,
    // UserServiceImpl#register'dagi "email yo'q -> darhol faollashtirish"
    // siyosati bilan bir xil g'oya). Parol o'zi HECH QACHON ishlatilmaydi
    // (foydalanuvchi bilmaydi) — tasodifiy, faqat DB "NOT NULL" talabini
    // qondirish uchun.
    private User createUser(long telegramId, String telegramUsername) {
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER bazada topilmadi"));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        byte[] randomPasswordBytes = new byte[24];
        random.nextBytes(randomPasswordBytes);
        String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomPasswordBytes);

        User user = User.builder()
                .username(generateUniqueUsername(telegramUsername, telegramId))
                .password(passwordEncoder.encode(randomPassword))
                .roles(roles)
                .telegramId(telegramId)
                .emailVerified(true)
                .build();

        return userRepository.save(user);
    }

    private String generateUniqueUsername(String telegramUsername, long telegramId) {
        String base = telegramUsername != null && !telegramUsername.isBlank()
                ? "tg_" + telegramUsername.toLowerCase().replaceAll("[^a-z0-9_]", "")
                : "tg_" + telegramId;

        if (base.equals("tg_")) {
            base = "tg_" + telegramId;
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + (++suffix);
        }
        return candidate;
    }
}
