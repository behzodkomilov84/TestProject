package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

// HAQIQIY TOPILGAN CHEKLOV (foydalanuvchi so'rovi, 2026-09-13: "Тестлар
// базасидаги айрим тестлар 1 дан ортиқ мавзуларга тушиши керак") — ilgari
// noyoblik (science_id, name) edi, ya'ni bitta Mavzu nomi BUTUN FAN
// (kurs) bo'yicha faqat BITTA marta bo'lishi mumkin edi, u qaysi Bo'limga
// (TopicSection) tegishli bo'lishidan qat'iy nazar —
// CourseService#resolveLinkedTopic shu sabab bir xil nomli Mavzuni
// (savollari bilan) YANGI Bo'limga "ko'chirib" qo'yardi, HAQIQIY
// MUSTAQIL NUSXA yaratish imkonsiz edi. Endi noyoblik BO'LIM darajasida
// (science_id, section_id, name) — bir xil nomli Mavzu turli Bo'limlarda
// mustaqil (bir-biriga bog'liq bo'lmagan) nusxa sifatida yashashi mumkin.
// DIQQAT: MySQL'da UNIQUE cheklovda NULL qiymatlar bir-biriga TENG
// hisoblanmaydi — ya'ni "section_id" NULL (hali Bo'limga ajratilmagan,
// "Mavzusiz") Mavzular bu cheklovdan MUSTASNO (bir xil nomli bir nechta
// "Mavzusiz" Mavzu ham bo'lishi mumkin) — bu ataylab shunday, chunki
// "Mavzusiz" holat allaqachon aniq guruhlanmagan.
@Entity
@Table(name = "topics",
        schema = "test_project",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_science_section_topic", columnNames = {"science_id", "section_id", "name"})
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.PERSIST)
    @ToString.Exclude
    private Set<Question> questions;

    @ManyToOne(fetch = FetchType.LAZY)
    private Science science;

    // Ixtiyoriy — "Bo'lim" (TopicSection) ichida guruhlangan bo'lsa
    // to'ldiriladi. NULL — hali bo'limga ajratilmagan (eski xulq-atvor:
    // tekis ro'yxat).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    @ToString.Exclude
    private TopicSection section;

    // Bo'lim ichidagi (yoki bo'limsiz fan ichidagi) tartib raqami.
    // Ilgari TopicRepository'da "ORDER BY t.id" ishlatilgan edi (haqiqiy
    // production bug — Kimyo 1-45 mavzulari aralashib qolgan edi); endi
    // shu aniq maydon orqali tartiblanadi.
    @Column(name = "order_index")
    private Integer orderIndex;

    // "O'chirilganlar savati" — Course.deletedAt bilan bir xil g'oya:
    // o'chirilganda DARHOL butunlay o'chmaydi (savollari ham saqlanib
    // qoladi), faqat shu maydon bilan belgilanadi — "♻️ Tiklash" bilan
    // bir zumda qaytadi.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
