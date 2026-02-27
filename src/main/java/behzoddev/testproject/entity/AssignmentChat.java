package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "assignment_chat",
        indexes = {
                @Index(name = "idx_chat_assignment", columnList = "assignment_id"),
                @Index(name = "idx_chat_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Assignment to which this message belongs
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_assignment"))
    private Assignment assignment;

    /**
     * Message sender (teacher or student)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_sender"))
    private User sender;

    /**
     * Message content
     */
    @Column(name = "message_text", nullable = false, length = 2000)
    private String messageText;

    /**
     * When message was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Soft delete flag (optional enterprise feature)
     */
    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

}
