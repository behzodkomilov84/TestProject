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

    // 255 emas — uzun (masalan klinik holat tavsifli) savollar MySQL
    // "Data too long" xatosiga olib kelib, butun Excel importni buzardi
    // (question-text-length.sql#91).
    @Column(nullable = false, length = 2000)
    private String questionText;

    // Savolga biriktirilgan rasm (masalan, geometrik chizma). Ixtiyoriy —
    // bo'lmasligi ham mumkin.
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // Rasmning tanlangan eni/bo'yi (piksel) — Answer.imageWidth/imageHeight
    // bilan bir xil g'oya (question.js#buildInlineImageWidget). NULL —
    // rasm o'zining tabiiy o'lchamida (CSS bo'yicha) ko'rsatiladi.
    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @OneToMany(mappedBy = "question",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @ToString.Exclude
    private List<Answer> answers;

    @ManyToOne(fetch = FetchType.LAZY)
    private Topic topic;

    // Shu mavzu ichidagi tartib raqami — A-Z/Z-A saralash va qo'lda
    // tartiblash (⬆⬇) imkoniyati uchun (Topic.orderIndex bilan bir xil
    // konvensiya).
    @Column(name = "order_index")
    private Integer orderIndex;

    // "O'chirilganlar savati" — Course.deletedAt bilan bir xil g'oya:
    // o'chirilganda DARHOL butunlay o'chmaydi (javoblari ham saqlanib
    // qoladi — Answer'lar CASCADE bilan avtomatik o'chirilmaydi, chunki
    // Question'ning o'zi endi o'chirilmayapti), faqat shu maydon bilan
    // belgilanadi — "♻️ Tiklash" bilan bir zumda qaytadi.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

}
