package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_session_questions")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class TestSessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private TestSession testSession;

    @ManyToOne(fetch = FetchType.LAZY)
    private Question question;

    // Eski (bitta javobli) yozuvlar uchun saqlanadi — YANGI yozuvlarda
    // ham to'ldiriladi (TANLANGAN javoblarning BIRINCHISI, orqaga moslik
    // uchun), lekin haqiqiy manba endi selectedAnswerIds hisoblanadi.
    @ManyToOne(fetch = FetchType.LAZY)
    private Answer selectedAnswer;

    // Ko'p to'g'ri javobli savollar uchun (foydalanuvchi so'rovi, 2026-09-05)
    // — talaba TANLAGAN barcha Answer.id'lar, vergul bilan ajratilgan
    // (masalan "12,14"). Bitta javobli savolda ham to'ldiriladi (bitta
    // id bilan) — TestSessionService shu yerdan o'qiydi, selectedAnswer
    // faqat eski o'qish yo'llari uchun qoladi.
    @Column(name = "selected_answer_ids")
    private String selectedAnswerIds;

    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;
}

