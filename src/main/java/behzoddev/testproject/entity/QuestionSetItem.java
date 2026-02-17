package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "question_set_items",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"set_id", "question_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionSetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id")
    private QuestionSet questionSet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;
}

