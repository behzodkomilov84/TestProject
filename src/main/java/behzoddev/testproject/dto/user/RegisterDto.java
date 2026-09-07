package behzoddev.testproject.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDto(
        @NotBlank(message = "❌Username bo'sh bo'lishi mumkin emas.") String username,
        // Ism/Familiya — "qaysi sohadan, qaysi kasbdagilar
        // foydalanayotganini" bilish uchun majburiy qilingan (foydalanuvchi
        // so'rovi, 2026-09-06). Tekshiruv UserServiceImpl#register'da
        // qo'lda (bu loyihada @Valid umuman ulanmagan — UserMvcController
        // #register @ModelAttribute'ni validatsiyasiz qabul qiladi, shu
        // sabab bu annotatsiyalar hozircha faqat hujjatlashtirish uchun,
        // haqiqiy tekshiruv service qatlamida).
        @NotBlank(message = "❌Ism bo'sh bo'lishi mumkin emas.") String firstName,
        @NotBlank(message = "❌Familiya bo'sh bo'lishi mumkin emas.") String lastName,
        // Ish/o'qish joyi va lavozim — MAJBURIY (foydalanuvchi so'rovi,
        // 2026-09-07: avval bu yerdan olib tashlangan, so'rov bilan
        // qaytadan registratsiya formasiga qo'shildi). @NotBlank shu
        // yerda YO'Q — sabab tepadagi izohda (@NotBlank umuman ulanmagan,
        // haqiqiy tekshiruv UserServiceImpl#register'da).
        String workplace,
        String jobTitle,
        // Email hamon IXTIYORIY — ko'pchilik foydalanuvchida email yo'q yoki
        // o'zi login/parolini bilmaydi (birov ochib bergan). Kiritilsa,
        // formati tekshiriladi (@Email bo'sh qatorni xato deb hisoblamaydi);
        // bo'sh qoldirilsa — UserServiceImpl.register() akkauntni
        // TASDIQLASHSIZ darhol faollashtiradi (vaqtinchalik yechim — kelgusida
        // SMS orqali tasdiqlashga almashtiriladi).
        @Email(message = "❌Email formati noto'g'ri.") String email,
        // Telefon — MAJBURIY (foydalanuvchi so'rovi, 2026-09-07).
        String phoneCountry,
        String phoneNumber,
        @NotBlank(message = "❌Password bo'sh bo'lishi mumkin emas.") @Size(min = 6, message = "Parolingiz kamida 6 xonali bo'lishi kerak") String password,
        @NotBlank(message = "❌ConfirmPassword bo'sh bo'lishi mumkin emas.") String confirmPassword) {

}
