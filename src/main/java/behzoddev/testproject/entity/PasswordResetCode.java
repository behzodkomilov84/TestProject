package behzoddev.testproject.entity;

import behzoddev.testproject.entity.enums.PasswordResetChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Parolni tiklash uchun bir martalik tasdiqlash kodi. Telegram orqali
 * (agar userda telegramId bo'lsa, ustuvor) yoki email orqali yuboriladi.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasswordResetChannel channel;

    @Builder.Default
    private boolean used = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
