package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "question_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Учитель — владелец набора
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    /**
     * Название набора
     */
    @Column(nullable = false)
    private String name;

    /**
     * Дата создания
     */
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Вопросы в наборе
     */
    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "question_set_items",
            joinColumns = @JoinColumn(name = "set_id"),
            inverseJoinColumns = @JoinColumn(name = "question_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"set_id", "question_id"}
            )
    )
    private Set<Question> questions = new HashSet<>();

    /*
     =========================
     Helper методы
     =========================
     */

    public void addQuestion(Question q) {
        questions.add(q);
    }

    public void removeQuestion(Question q) {
        questions.remove(q);
    }

    public int size() {
        return questions.size();
    }
}
