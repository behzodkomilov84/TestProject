package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Table(name = "science", schema = "test_project")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
@NamedEntityGraph(
        name = "scienceWithTopics",
        attributeNodes = {
                @NamedAttributeNode(value = "topics", subgraph = "topicsWithQuestions")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "topicsWithQuestions",
                        attributeNodes = {
                                @NamedAttributeNode("questions")
                        }
                )
        }
)
public class Science {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "science", cascade = CascadeType.PERSIST)
    @ToString.Exclude
    private Set<Topic> topics;
}
