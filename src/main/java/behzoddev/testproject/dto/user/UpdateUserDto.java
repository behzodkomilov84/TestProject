package behzoddev.testproject.dto.user;

// OWNER "Foydalanuvchilar" sahifasidan (userManagerPage.html) foydalanuvchi
// ma'lumotlarini qo'lda tahrirlashi uchun (foydalanuvchi so'rovi, 2026-09-07:
// "sahifasiga edit ni qo'shish kerak"). Telegram ID/Google ID/Telegram
// @username BU YERDA tahrirlanmaydi — ular faqat tegishli hisob orqali
// bog'lanish/uzish orqali o'zgaradi, qo'lda emas.
public record UpdateUserDto(
        String username,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String workplace,
        String jobTitle
) {
}
