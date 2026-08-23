package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

// Kurs ICHIDAGI "Bo'lim" (bob/chapter) — bir nechta CourseSection
// (mavzu/lesson)ni guruhlaydi, kurs sahifasida alohida "box" sifatida
// ko'rsatish uchun (masalan "1-BOB: Kirish", "2-BOB: Asosiy tushunchalar").
// DIQQAT: bu topic_sections (Fan -> Bo'lim -> Mavzu, test bazasi
// ierarxiyasi) bilan ALOQASI YO'Q — butunlay boshqa, kurs kontekstidagi
// tushuncha, ataylab boshqa nom (CourseChapter) bilan, chalkashmaslik uchun.
@Entity
@Table(name = "course_chapters")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourseChapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 255)
    private String name;

    // Kurs ichidagi tartib raqami — bo'lim "box"lari shu bo'yicha ketma-ket
    // ko'rsatiladi (yangi bo'lim doim oxiriga qo'shiladi).
    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
