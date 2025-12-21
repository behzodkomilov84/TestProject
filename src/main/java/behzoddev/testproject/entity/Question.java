package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "questions", schema = "test_project")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
@NamedEntityGraph(
        name = "questionWithAnswers",
        attributeNodes = {
                @NamedAttributeNode("answers")
        }
)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String questionText;

    @OneToMany(mappedBy = "question",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @ToString.Exclude
    private List<Answer> answers;

    @ManyToOne(fetch = FetchType.LAZY)
    private Topic topic;

}
