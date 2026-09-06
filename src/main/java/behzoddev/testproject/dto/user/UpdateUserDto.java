package behzoddev.testproject.dto.user;

// OWNER "Foydalanuvchilar" sahifasidan (userManagerPage.html) foydalanuvchi
// ma'lumotlarini qo'lda tahrirlashi uchun (foydalanuvchi so'rovi, 2026-09-07:
// "sahifasiga edit ni qo'shish kerak", keyin "qolgan polyalarni ham
// qo'shish kerak" — Telegram ID/@username/Google ID ham). Bular odatda
// tegishli hisob orqali bog'lanish/uzish bilan o'zgaradi, lekin OWNER
// noto'g'ri/eskirgan qiymatni qo'lda tuzatishi kerak bo'lishi mumkin
// (masalan, dublikat-hisob muammolarini debug qilishda).
// "telegramId" String sifatida qabul qilinadi (forma matn input'i) —
// UserServiceImpl.adminUpdateUser ichida Long'ga o'giriladi.
public record UpdateUserDto(
        String username,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String workplace,
        String jobTitle,
        String telegramId,
        String telegramUsername,
        String googleId
) {
}
