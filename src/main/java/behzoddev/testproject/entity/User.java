package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString(exclude = {"password", "roles"})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // ❗ Bitta akkaunt bir nechta rolga ega bo'lishi mumkin (masalan, ham
    // o'qituvchi — ROLE_ADMIN, ham o'quvchi — ROLE_USER). EAGER majburiy —
    // rollar avtorizatsiya vaqtida darhol kerak bo'ladi.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "group_id")
    private TeacherGroup group;

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    // Parolni tiklash uchun (email kanali). Eski userlarda bo'sh bo'lishi
    // mumkin — faqat yangi ro'yxatdan o'tishda majburiy qilingan.
    @Column(unique = true)
    private String email;

    // Brute-force himoyasi: ketma-ket noto'g'ri parol urinishlari soni.
    // Muvaffaqiyatli login'da 0'ga tushiriladi.
    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    // Shu vaqtgacha hisob bloklangan (5-marta noto'g'ri urinishdan keyin).
    // null bo'lsa — bloklanmagan.
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Foydalanuvchida berilgan nomdagi rol bor-yo'qligini tekshiradi.
     * Masalan: user.hasRole("ROLE_ADMIN")
     */
    public boolean hasRole(String roleName) {
        return roles != null && roles.stream()
                .anyMatch(r -> roleName.equals(r.getRoleName()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles == null
                ? Set.of()
                : roles.stream()
                        .map(r -> new SimpleGrantedAuthority(r.getRoleName()))
                        .collect(Collectors.toSet());
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
