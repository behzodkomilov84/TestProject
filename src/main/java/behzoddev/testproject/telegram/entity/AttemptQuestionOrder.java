package behzoddev.testproject.telegram.entity;

import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.Question;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attempt_question_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptQuestionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private AssignmentAttempt attempt;

    @ManyToOne
    private Question question;

    private int position;
}
