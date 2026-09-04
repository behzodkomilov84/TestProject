package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// JavaRush uslubidagi online kurs — OWNER tomonidan yaratiladi, ADMIN/USER
// obuna orqali kirish huquqini sotib oladi (CourseSubscription).
@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    // false = qoralama (faqat OWNER ko'radi), true = chop etilgan (katalogda hammaga ko'rinadi).
    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    // true bo'lsa — obunasiz ham (site'da HAM, Telegram bot'da HAM) hammaga
    // to'liq ochiq (CourseService.isSubscribed() shuni tekshiradi).
    @Column(nullable = false)
    @Builder.Default
    private boolean free = false;

    // Pullik kursning ko'rsatiladigan (ma'lumot uchun) narxi — free=false
    // bo'lganda mazmunli. Haqiqiy obuna summasi baribir OWNER tomonidan
    // qo'lda kiritiladi (CourseSubscription.amount) — bu shunchaki katalog/
    // kurs sahifasida "narxi qancha" ko'rsatish uchun.
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    // "Bo'lim" (Course) qaysi "Yo'nalish" (CourseField) ostida ekani —
    // DB darajasida ixtiyoriy (Yo'nalish o'chirilsa ON DELETE SET NULL —
    // kurs "yetim" bo'lib qoladi, keyin qo'lda boshqasiga o'tkaziladi),
    // lekin YANGI kurs yaratishda ilova darajasida MAJBURIY talab
    // qilinadi (CourseService.createCourse — foydalanuvchi so'rovi,
    // 2026-09-04, "aralashib ketmasin" deb).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id")
    private CourseField field;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // "O'chirilganlar savati" — kurs "🗑️ O'chirish" bosilganda DARHOL
    // butunlay o'chirilmaydi, faqat shu maydon bilan belgilanadi (bo'lim/
    // mavzu/obuna/progress ma'lumotlari HAM tegilmay saqlanadi) — bir
    // vaqt ichida CourseService.restoreCourse orqali qaytadan tiklash
    // mumkin bo'lishi uchun. NULL — o'chirilmagan (odatiy holat).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ROLE_ADMIN o'z kursini "🗑️ Butunlay o'chirish" desa — ROLE_OWNER'dan
    // farqli, HAQIQIY (bazadan) o'chirilmaydi: shu maydonlar to'ldiriladi
    // (CourseService.permanentlyDeleteCourse), kurs shu ADMIN va
    // katalogdan yo'qoladi, lekin ROLE_OWNER hali ham ko'ra oladi (kim,
    // qachon o'chirgani bilan) va xohlasa o'z nomiga o'tkazib qayta
    // tiklashi mumkin (reclaimArchivedCourse). NULL — arxivlanmagan
    // (odatiy holat).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by_admin_id")
    private User archivedByAdmin;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
