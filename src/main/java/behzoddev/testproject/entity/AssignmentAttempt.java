package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignment_attempts",
        uniqueConstraints=@UniqueConstraint(
                name = "uq_attempt",
                columnNames={"assignment_id","pupil_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentAttempt {

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
            mappedBy = "assignmentAttempt",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<AttemptAnswer> answers = new ArrayList<>();

    private LocalDateTime lastSync;

    /**
     * Удобный helper для добавления ответа
     */
    public void addAnswer(AttemptAnswer answer) {
        answers.add(answer);
        answer.setAssignmentAttempt(this);
    }

    /**
     * Удобный helper для очистки ответов
     */
    public void clearAnswers() {
        answers.forEach(a -> a.setAssignmentAttempt(null));
        answers.clear();
    }

    public boolean isStarted() {
        return startedAt != null;
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

}
