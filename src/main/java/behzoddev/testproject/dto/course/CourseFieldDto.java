package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.time.LocalDateTime;

// "Yo'nalish" (soha) — katalog kartochkasi uchun (coursesCatalog.js).
@Builder
public record CourseFieldDto(
        Long id,
        String name,
        int orderIndex,
        int courseCount, // shu Yo'nalishdagi FAOL (o'chirilmagan) Bo'limlar (Course) soni
        // shu Yo'nalishdagi FAOL (o'chirilmagan) Bo'limlar (Science, TEST
        // BOSHQARUVI tomonida) soni — science.js#renderFieldBox uchun.
        int scienceCount,
        LocalDateTime createdAt,
        // Ixtiyoriy — faqat "O'chirilganlar savati" ro'yxatida to'ldiriladi.
        LocalDateTime deletedAt,
        // Yo'nalishning O'ZI barcha OWNER/ADMIN'ga ko'rinadi (kurs/fan
        // yaratishda tanlash uchun umumiy/sherik resurs), lekin
        // tahrirlash/o'chirish FAQAT yaratgan ADMIN'ga (yoki cheklovsiz
        // OWNER'ga) ochiq — foydalanuvchi so'rovi, 2026-09-08:
        // "/science/fields sahifasida... agar yo'nalishlarni admin o'zi
        // yaratmagan bo'lsa tahrirlash, o'chirishlarni hidden qilib
        // qo'y". Reorder (⬆⬇) ATAYLAB bunga kirmaydi — Science/Course
        // reorder bilan bir xil sabab (bir nechta egaga tegishli aralash
        // ro'yxat).
        boolean canManage
) {
}
