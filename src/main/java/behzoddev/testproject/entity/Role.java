package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

@Entity
@Table(name = "roles")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class Role implements GrantedAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roleName;

    // Bitta rolga bir nechta foydalanuvchi ega bo'lishi mumkin va aksincha
    // (dual-role: masalan, bitta odam ham ROLE_ADMIN, ham ROLE_USER bo'lishi mumkin).
    @ManyToMany(mappedBy = "roles")
    @ToString.Exclude
    private Set<User> users;

    @Override
    public @Nullable String getAuthority() {
        return roleName;
    }
}
