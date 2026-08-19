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
    AWAITING_NEW_PASSWORD,

    // ===== Mustaqil test (fan/savol soni tanlash inline tugmalar orqali,
    // lekin bu holat "/cancel" va boshqa menyu tugmalarini bloklash uchun) =====
    IN_PRACTICE_TEST,

    // ===== ADMIN (o'qituvchi): gruppalar, topshiriqlar, chat, savol import =====
    AWAITING_GROUP_NAME,
    AWAITING_INVITE_USERNAME,
    AWAITING_CHAT_MESSAGE,
    AWAITING_EXCEL_FILE,

    // ===== OWNER: foydalanuvchilar, sozlamalar, e'lon =====
    AWAITING_USER_SEARCH,
    AWAITING_MIN_AMOUNT,
    AWAITING_BROADCAST_TEXT
}
