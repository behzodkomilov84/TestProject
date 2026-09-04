package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// "Yo'nalish" (soha) — Kurslar (endi "Bo'lim" deb ataladi, Course entity)
// katalogini kattaroq guruhga bo'ladi (masalan "Sanitariya epidemiologiya
// xizmati", "O'rta ta'lim"), turli sohalarga tegishli kurslar bitta tekis
// ro'yxatda aralashib ketmasligi uchun (foydalanuvchi so'rovi, 2026-09-04).
// Course -> CourseField bog'lanishi IXTIYORIY (field=null — "Yo'nalishsiz
// kurslar" psevdo-guruhida ko'rinadi), lekin YANGI kurs yaratishda ilova
// darajasida MAJBURIY talab qilinadi (CourseService.createCourse).
@Entity
@Table(name = "course_fields")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourseField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    // Katalogda ketma-ket ko'rsatish uchun tartib raqami — yangi Yo'nalish
    // doim oxiriga qo'shiladi (CourseChapter.orderIndex bilan bir xil g'oya).
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // "O'chirilganlar savati" — Course.deletedAt bilan bir xil g'oya:
    // o'chirilganda DARHOL butunlay o'chmaydi, faqat shu maydon bilan
    // belgilanadi. NULL — o'chirilmagan (odatiy holat).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
