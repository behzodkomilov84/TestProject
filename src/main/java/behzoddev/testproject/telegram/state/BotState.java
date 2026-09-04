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

    // ===== Mustaqil test (bo'lim/savol soni tanlash inline tugmalar orqali,
    // lekin bu holat "/cancel" va boshqa menyu tugmalarini bloklash uchun) =====
    IN_PRACTICE_TEST,
    // Foydalanuvchi tayyor tugmalar o'rniga savollar sonini o'zi qo'lda
    // yozib kiritayotgan holat ("✏️ O'zi kiritish" bosilgandan keyin).
    AWAITING_PT_CUSTOM_COUNT,
    // Xuddi shunday — Exam/Hard rejimida vaqt chegarasini (daqiqa) o'zi
    // qo'lda kiritayotgan holat.
    AWAITING_PT_CUSTOM_TIME,

    // ===== ADMIN (o'qituvchi): gruppalar, topshiriqlar, chat, savol import =====
    AWAITING_GROUP_NAME,
    AWAITING_INVITE_USERNAME,
    AWAITING_CHAT_MESSAGE,
    AWAITING_EXCEL_FILE,

    // ===== OWNER: foydalanuvchilar, sozlamalar, e'lon =====
    AWAITING_USER_SEARCH,
    AWAITING_MIN_AMOUNT,
    AWAITING_BROADCAST_TEXT,

    // ===== Botda to'g'ridan-to'g'ri ro'yxatdan o'tish =====
    AWAITING_REG_USERNAME,
    AWAITING_REG_EMAIL,
    AWAITING_REG_PHONE,
    AWAITING_REG_PASSWORD,
    AWAITING_REG_CONFIRM_PASSWORD,
    AWAITING_REG_TERMS,
    AWAITING_REG_EMAIL_CODE
}
