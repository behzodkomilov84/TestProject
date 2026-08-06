package behzoddev.testproject.entity.enums;

public enum CourseSubscriptionStatus {
    // Foydalanuvchi "obuna bo'lishni xohlayman" so'rovini yubordi,
    // OWNER hali ko'rib chiqmagan.
    PENDING,
    // OWNER tomonidan qo'lda tasdiqlangan — kursga kirish huquqi endDate'gacha faol.
    CONFIRMED,
    // Muddati o'tgan (kunlik scheduled job avtomatik belgilaydi).
    EXPIRED,
    // OWNER tomonidan bekor qilingan/rad etilgan.
    CANCELLED
}
