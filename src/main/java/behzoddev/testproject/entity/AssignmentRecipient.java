package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "assignment_recipients",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_assignment_student",
                        columnNames = {"assignment_id", "pupil_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pupil_id", nullable = false)
    private User pupil;
}

