package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
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

    // Fanlar ro'yxatidagi tartib raqami — A-Z/Z-A saralash va qo'lda
    // tartiblash (⬆⬇) imkoniyati uchun (TopicSection.orderIndex bilan
    // bir xil konvensiya).
    @Column(name = "order_index")
    private Integer orderIndex;

    // "O'chirilganlar savati" — Course.deletedAt bilan bir xil g'oya:
    // o'chirilganda DARHOL butunlay o'chmaydi (Bo'lim/mavzu/savollari
    // ham saqlanib qoladi), faqat shu maydon bilan belgilanadi —
    // "♻️ Tiklash" bilan bir zumda qaytadi.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
