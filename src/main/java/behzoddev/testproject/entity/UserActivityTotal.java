package behzoddev.testproject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// "/users" sahifasidagi "Oxirgi tashrif vaqti" katakchasi bosilganda —
// "Saytda jami necha soat" ko'rsatkichi uchun (foydalanuvchi so'rovi,
// 2026-09-10). UserActivityTracker orqali to'planadi/yangilanadi.
// PK to'g'ridan-to'g'ri User.id ("1:1", @GeneratedValue YO'Q — qiymat
// har doim mavjud foydalanuvchi ID'sidan olinadi).
@Entity
@Table(name = "user_activity_totals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityTotal {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_seconds", nullable = false)
    @Builder.Default
    private long totalSeconds = 0;
}
