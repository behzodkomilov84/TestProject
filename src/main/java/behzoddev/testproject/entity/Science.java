package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Table(
        name = "science",
        schema = "test_project",
        uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString

@NamedEntityGraph(name = "scienceWithTopics", attributeNodes = {@NamedAttributeNode(value = "topics", subgraph = "topicsWithQuestions")}, subgraphs = {@NamedSubgraph(name = "topicsWithQuestions", attributeNodes = {@NamedAttributeNode(value = "questions", subgraph = "questionWithAnswers")}), @NamedSubgraph(name = "questionWithAnswers", attributeNodes = {@NamedAttributeNode("answers")})})

public class Science {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "science", cascade = CascadeType.PERSIST)
    @ToString.Exclude
    private Set<Topic> topics;
}
