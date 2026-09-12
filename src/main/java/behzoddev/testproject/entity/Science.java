package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(
        name = "science",
        schema = "test_project",
        uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString

@NamedEntityGraph(name = "scienceWithTopics", attributeNodes = {@NamedAttributeNode(value = "topics", subgraph = "topicsWithQuestions")}, subgraphs = {@NamedSubgraph(name = "topicsWithQuestions", attributeNodes = {@NamedAttributeNode(value = "questions", subgraph = "questionWithAnswers")}), @NamedSubgraph(name = "questionWithAnswers", attributeNodes = {@NamedAttributeNode("answers")})})

public class Science {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 500)
    private String name;

    @OneToMany(mappedBy = "science", cascade = CascadeType.PERSIST)
    @ToString.Exclude
    private Set<Topic> topics;

    // Fanlar (UI'da "Bo'lim") ro'yxatidagi tartib raqami — A-Z/Z-A
    // saralash va qo'lda tartiblash (⬆⬇) imkoniyati uchun
    // (TopicSection.orderIndex bilan bir xil konvensiya).
    @Column(name = "order_index")
    private Integer orderIndex;

    // Qaysi Yo'nalishga (CourseField, Kurslar bilan UMUMIY) tegishli —
    // ixtiyoriy (foydalanuvchi so'rovi, 2026-09-04). NULL — hali
    // Yo'nalishga tayinlanmagan (eski, migratsiyadan oldingi) fanlar.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id")
    private CourseField field;

    // "O'chirilganlar savati" — Course.deletedAt bilan bir xil g'oya:
    // o'chirilganda DARHOL butunlay o'chmaydi (Bo'lim/mavzu/savollari
    // ham saqlanib qoladi), faqat shu maydon bilan belgilanadi —
    // "♻️ Tiklash" bilan bir zumda qaytadi.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Fanni kim yaratgani — Course.createdBy bilan bir xil g'oya, ADMIN
    // faqat O'ZI yaratgan fanni (va uning ichidagi Bo'lim/Mavzu/Savollarni)
    // boshqara olishi uchun (foydalanuvchi so'rovi, 2026-09-08: "ROLE_ADMIN
    // o'zi yaratmagan hech qaysi joyda o'zgartirish qila olmasin"). Course'dan
    // farqli — NULLable (mavjud fanlar migratsiyadan oldin muallifsiz
    // yaratilgan edi; barchasi science-created-by.sql orqali tayinlandi,
    // amalda NULL bo'lmaydi, lekin ustun DB darajasida ixtiyoriy qoldirilgan).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
