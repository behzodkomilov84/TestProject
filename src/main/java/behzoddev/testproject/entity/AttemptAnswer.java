package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attempt_answers",
        uniqueConstraints =
        @UniqueConstraint(
                columnNames={"attempt_id","question_id"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Попытка прохождения задания
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private AssignmentAttempt assignmentAttempt;

    /**
     * Вопрос
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /**
     * Выбранный ответ (может быть null — если ученик пропустил). Eski
     * (bitta javobli) yozuvlar uchun saqlanadi — YANGI yozuvlarda ham
     * to'ldiriladi (TANLANGAN javoblarning BIRINCHISI, orqaga moslik
     * uchun, Telegram bot ham hozircha shu maydondan foydalanadi).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_answer_id")
    private Answer selectedAnswer;

    // Ko'p to'g'ri javobli savollar uchun (foydalanuvchi so'rovi, 2026-09-05)
    // — talaba TANLAGAN barcha Answer.id'lar, vergul bilan ajratilgan
    // (masalan "12,14"). AssignmentAttemptService shu yerdan o'qiydi.
    @Column(name = "selected_answer_ids")
    private String selectedAnswerIds;

    /**
     * Был ли ответ правильным
     */
    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private boolean correct = false;
}
