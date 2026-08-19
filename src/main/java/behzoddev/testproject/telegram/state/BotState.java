package behzoddev.testproject.telegram.state;

// Botdagi ko'p bosqichli suhbatning joriy bosqichi (TelegramSession.state).
// Yangi oqimlar (gruppa yaratish, topshiriq berish va h.k.) qo'shilganda
// shu yerga yangi qiymatlar qo'shiladi.
public enum BotState {

    // Erkin holat — foydalanuvchi hech qanday ko'p bosqichli oqim
    // o'rtasida emas, oddiy menyu buyruqlari kutilmoqda.
    NONE,

    // ===== Profil tahrirlash =====
    AWAITING_USERNAME,
    AWAITING_EMAIL,
    AWAITING_PHONE,
    AWAITING_CURRENT_PASSWORD,
    AWAITING_NEW_PASSWORD
}
