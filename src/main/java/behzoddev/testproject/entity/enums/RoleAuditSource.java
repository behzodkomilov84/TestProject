package behzoddev.testproject.entity.enums;

// Rol o'zgarishi qayerdan kelgani — audit tarixida kim/nima sabab bo'lganini
// ajratish uchun.
public enum RoleAuditSource {
    // OWNER /users sahifasidagi checkbox orqali qo'lda bergan/olib tashlagan.
    MANUAL,
    // Subscription (to'lov/obuna) orqali berilgan/muddati tugab olib tashlangan.
    SUBSCRIPTION,
    // Foydalanuvchi ishtirokisiz, tizim tomonidan (masalan scheduled job).
    SYSTEM
}
