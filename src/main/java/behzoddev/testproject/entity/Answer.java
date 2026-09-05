package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "answers", schema = "test_project")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String answerText;

    @Builder.Default
    private Boolean isTrue = false;

    private String commentary;

    // Javob variantiga biriktirilgan rasm (masalan, geometrik chizma). Ixtiyoriy.
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // Rasmning tanlangan eni/bo'yi (piksel) — foydalanuvchi "Savol formasi"da
    // qo'lda kiritadi/o'zgartiradi (question.js#buildInlineImageWidget).
    // NULL — rasm o'zining tabiiy o'lchamida (CSS bo'yicha) ko'rsatiladi.
    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    // Izoh (commentary) ichida ham rasm, ham video bo'lishi mumkin — matn
    // bilan bir qatorda, matnni almashtirmaydi.
    @Column(name = "commentary_image_url", length = 500)
    private String commentaryImageUrl;

    @Column(name = "commentary_video_url", length = 500)
    private String commentaryVideoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;


}
