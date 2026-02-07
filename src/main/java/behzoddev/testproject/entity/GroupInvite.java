package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_invites",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"group_id", "pupil_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Группа, в которую приглашают
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private TeacherGroup group;

    // Ученик, которого приглашают
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pupil_id", nullable = false)
    private User pupil;

    // Статус приглашения (PENDING, ACCEPTED, REJECTED)
    @Builder.Default
    @Column(nullable = false)
    private String status = "PENDING";

    // Булево поле для упрощённой проверки, принят ли запрос
    @Builder.Default
    @Column(nullable = false)
    private Boolean accepted = false;

    // Время создания приглашения
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
