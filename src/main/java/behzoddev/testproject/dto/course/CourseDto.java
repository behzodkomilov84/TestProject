package behzoddev.testproject.dto.course;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Kurs katalogi (ro'yxat) uchun — bo'lim kontentisiz, umumiy ma'lumot.
@Builder
public record CourseDto(
        Long id,
        String title,
        String description,
        String coverImageUrl,
        boolean published,
        boolean free,
        BigDecimal price,
        int sectionCount,
        // Kurs katalogi kartochkasida ALOHIDA ko'rsatish uchun (courses.js)
        // — sectionCount MAVZULAR (CourseSection) soni, bu esa haqiqiy
        // Bo'limlar (CourseChapter) soni. Ilgari kartochkada FAQAT
        // sectionCount "N ta bo'lim" deb NOTO'G'RI belgilab ko'rsatilardi
        // (haqiqiy foydalanuvchi shikoyati — aslida mavzular soni edi).
        int chapterCount,
        boolean subscribed, // joriy foydalanuvchi obuna bo'lganmi (yoki kurs bepul/canManage)
        LocalDateTime createdAt,
        // Ixtiyoriy — faqat "O'chirilganlar savati" ro'yxatida to'ldiriladi
        // (qachon o'chirilganini ko'rsatish uchun). Oddiy katalogda har
        // doim null (soft-deleted kurslar u yerda umuman ko'rinmaydi).
        LocalDateTime deletedAt
) {
}
