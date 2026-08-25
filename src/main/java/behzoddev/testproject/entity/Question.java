package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "questions", schema = "test_project")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
@NamedEntityGraph(
        name = "questionWithAnswers",
        attributeNodes = {
                @NamedAttributeNode("answers")
        }
)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String questionText;

    // Savolga biriktirilgan rasm (masalan, geometrik chizma). Ixtiyoriy —
    // bo'lmasligi ham mumkin.
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @OneToMany(mappedBy = "question",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @ToString.Exclude
    private List<Answer> answers;

    @ManyToOne(fetch = FetchType.LAZY)
    private Topic topic;

    // "O'chirilganlar savati" — Course.deletedAt bilan bir xil g'oya:
    // o'chirilganda DARHOL butunlay o'chmaydi (javoblari ham saqlanib
    // qoladi — Answer'lar CASCADE bilan avtomatik o'chirilmaydi, chunki
    // Question'ning o'zi endi o'chirilmayapti), faqat shu maydon bilan
    // belgilanadi — "♻️ Tiklash" bilan bir zumda qaytadi.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

}
