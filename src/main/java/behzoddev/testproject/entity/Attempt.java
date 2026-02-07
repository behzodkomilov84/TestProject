package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignment_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Назначенное задание
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    /**
     * Ученик, который проходит тест
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pupil_id", nullable = false)
    private User pupil;

    /**
     * Статистика попытки
     */
    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers;

    @Column(name = "percent", nullable = false)
    private int percent;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    /**
     * Время начала/окончания
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * Ответы ученика
     */
    @Builder.Default
    @OneToMany(
            mappedBy = "attempt",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<AttemptAnswer> answers = new ArrayList<>();

    /**
     * Удобный helper для добавления ответа
     */
    public void addAnswer(AttemptAnswer answer) {
        answers.add(answer);
        answer.setAttempt(this);
    }

    /**
     * Удобный helper для очистки ответов
     */
    public void clearAnswers() {
        answers.forEach(a -> a.setAttempt(null));
        answers.clear();
    }
}
