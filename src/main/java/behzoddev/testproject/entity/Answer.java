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

    private Boolean isTrue;

    private String commentary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;


}
