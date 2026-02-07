package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Набор вопросов, на основе которого создано задание
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    private QuestionSet questionSet;

    /**
     * Группа учеников (опционально)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private TeacherGroup group;

    /**
     * Конкретный ученик (опционально)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pupil_id")
    private User pupil;

    /**
     * Учитель, который назначил задание
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    /**
     * Когда задание было назначено
     */
    @Column(name = "assigned_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    /**
     * Дедлайн (может быть null)
     */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /**
     * Попытки прохождения
     */
    @Builder.Default
    @OneToMany(
            mappedBy = "assignment",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Attempt> attempts = new ArrayList<>();

    /**
     * Helper — добавить попытку
     */
    public void addAttempt(Attempt attempt) {
        attempts.add(attempt);
        attempt.setAssignment(this);
    }

    /**
     * Helper — удалить попытку
     */
    public void removeAttempt(Attempt attempt) {
        attempts.remove(attempt);
        attempt.setAssignment(null);
    }

    /**
     * Бизнес-проверка:
     * задание должно быть назначено либо группе, либо ученику
     */
    @PrePersist
    @PreUpdate
    private void validateTarget() {
        if (group == null && pupil == null) {
            throw new IllegalStateException(
                    "Assignment must target either a group or a pupil"
            );
        }
    }
}
