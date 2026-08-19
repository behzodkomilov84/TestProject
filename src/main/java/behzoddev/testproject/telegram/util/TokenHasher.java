package behzoddev.testproject.telegram.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Bitta martalik tokenlarni (masalan Telegram auto-login) bazada XOM
// holda emas, SHA-256 xesh sifatida saqlash uchun. Sabab: token o'zi
// URL query-string'da (Telegram xabarida, server/nginx access log'larida,
// brauzer tarixida) ko'rinadi — agar baza kimdir tomonidan (masalan
// zaxira nusxasi yoki faqat-o'qish huquqi bilan) o'qilsa, xesh orqali
// saqlangan qiymatdan asl tokenni tiklab bo'lmaydi, shuning uchun
// bazadagi yozuvning o'zi hech kimga foydali emas.
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 har qanday standart JVM'da mavjud — bu holat amalda
            // yuz bermaydi, lekin checked exception'ni tozalash uchun.
            throw new IllegalStateException("SHA-256 mavjud emas", e);
        }
    }
}
