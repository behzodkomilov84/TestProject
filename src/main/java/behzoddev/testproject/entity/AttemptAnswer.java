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
     * Выбранный ответ (может быть null — если ученик пропустил)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_answer_id")
    private Answer selectedAnswer;

    /**
     * Был ли ответ правильным
     */
    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private boolean correct = false;
}
