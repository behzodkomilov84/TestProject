package behzoddev.testproject.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDto(
        @NotBlank(message = "❌Username bo'sh bo'lishi mumkin emas.") String username,
        // Email ENDI IXTIYORIY — ko'pchilik foydalanuvchida email yo'q yoki
        // o'zi login/parolini bilmaydi (birov ochib bergan). Kiritilsa,
        // formati tekshiriladi (@Email bo'sh qatorni xato deb hisoblamaydi);
        // bo'sh qoldirilsa — UserServiceImpl.register() akkauntni
        // TASDIQLASHSIZ darhol faollashtiradi (vaqtinchalik yechim — kelgusida
        // SMS orqali tasdiqlashga almashtiriladi).
        @Email(message = "❌Email formati noto'g'ri.") String email,
        // Telefon ixtiyoriy — bo'sh qoldirilsa ro'yxatdan o'tishda muammo bo'lmaydi.
        String phoneCountry,
        String phoneNumber,
        @NotBlank(message = "❌Password bo'sh bo'lishi mumkin emas.") @Size(min = 6, message = "Parolingiz kamida 6 xonali bo'lishi kerak") String password,
        @NotBlank(message = "❌ConfirmPassword bo'sh bo'lishi mumkin emas.") String confirmPassword) {

}
