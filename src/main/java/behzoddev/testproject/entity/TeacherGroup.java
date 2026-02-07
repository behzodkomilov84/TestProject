package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "teacher_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Учитель — владелец группы
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    /**
     * Название группы
     */
    @Column(nullable = false)
    private String name;

    /**
     * Дата создания
     */
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Ученики в группе
     */
    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "group_members",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "pupil_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"group_id", "pupil_id"}
            )
    )
    private Set<User> pupils = new HashSet<>();

    /*
     =========================
     Helper методы
     =========================
     */

    public void addPupil(User user) {
        pupils.add(user);
    }

    public void removePupil(User user) {
        pupils.remove(user);
    }

    public int size() {
        return pupils.size();
    }
}
