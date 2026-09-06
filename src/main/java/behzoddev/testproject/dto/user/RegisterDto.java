package behzoddev.testproject.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDto(
        @NotBlank(message = "❌Username bo'sh bo'lishi mumkin emas.") String username,
        // Ism/Familiya/Ish-o'qish joyi/Lavozim — "qaysi sohadan, qaysi
        // kasbdagilar foydalanayotganini" bilish uchun majburiy qilingan
        // (foydalanuvchi so'rovi, 2026-09-06). Tekshiruv UserServiceImpl
        // #register'da qo'lda (bu loyihada @Valid umuman ulanmagan —
        // UserMvcController#register @ModelAttribute'ni validatsiyasiz
        // qabul qiladi, shu sabab bu annotatsiyalar hozircha faqat
        // hujjatlashtirish uchun, haqiqiy tekshiruv service qatlamida).
        @NotBlank(message = "❌Ism bo'sh bo'lishi mumkin emas.") String firstName,
        @NotBlank(message = "❌Familiya bo'sh bo'lishi mumkin emas.") String lastName,
        @NotBlank(message = "❌Ish yoki o'qish joyingizni kiriting.") String workplace,
        @NotBlank(message = "❌Lavozimingizni kiriting.") String jobTitle,
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
