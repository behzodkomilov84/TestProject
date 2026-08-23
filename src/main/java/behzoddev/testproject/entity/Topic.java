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

    // Ixtiyoriy — "Bo'lim" (TopicSection) ichida guruhlangan bo'lsa
    // to'ldiriladi. NULL — hali bo'limga ajratilmagan (eski xulq-atvor:
    // tekis ro'yxat).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    @ToString.Exclude
    private TopicSection section;

    // Bo'lim ichidagi (yoki bo'limsiz fan ichidagi) tartib raqami.
    // Ilgari TopicRepository'da "ORDER BY t.id" ishlatilgan edi (haqiqiy
    // production bug — Kimyo 1-45 mavzulari aralashib qolgan edi); endi
    // shu aniq maydon orqali tartiblanadi.
    @Column(name = "order_index")
    private Integer orderIndex;
}
