package behzoddev.testproject.entity;

import behzoddev.testproject.entity.enums.SubscriptionSource;
import behzoddev.testproject.entity.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ROLE_ADMIN'ga muddatli (obuna asosida) o'tish uchun to'lov yozuvi.
 * <p>
 * Bitta foydalanuvchida bir nechta Subscription bo'lishi mumkin (tarix
 * sifatida saqlanadi). Faqat status=CONFIRMED va endDate hali o'tmagan
 * eng so'nggi yozuv "faol obuna" hisoblanadi.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // To'lov qanday kanal orqali kelgani: OWNER qo'lda kiritdimi, Telegram
    // bot orqali so'rov yuborilganmi, yoki (kelajakda) onlayn to'lov shlyuzi.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionSource source;

    // PENDING — tasdiq kutilmoqda (masalan, Telegram orqali kelgan so'rov).
    // CONFIRMED — OWNER tasdiqlagan, ADMIN roli faol.
    // EXPIRED — muddati tugagan (scheduled job avtomatik belgilaydi).
    // CANCELLED — OWNER rad etgan.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    // Qaysi OWNER tasdiqlagani (audit uchun).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
