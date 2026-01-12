package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Table(name = "topics",
        schema = "test_project",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"science_id", "name"})
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.PERSIST)
    @ToString.Exclude
    private Set<Question> questions;

    @ManyToOne(fetch = FetchType.LAZY)
    private Science science;
}
