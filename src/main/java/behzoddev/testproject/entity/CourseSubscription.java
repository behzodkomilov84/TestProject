package behzoddev.testproject.entity;

import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Foydalanuvchining bitta kursga muddatli kirish huquqi (ADMIN-rol
// obunasi bilan bir xil g'oyada — startDate/endDate) — foydalanuvchi
// so'rov yuboradi (PENDING) yoki OWNER to'g'ridan-to'g'ri beradi,
// ikkala holatda ham yakuniy tasdiqlash OWNER tomonidan (hozircha
// Telegram orqali avtomatik oqim yo'q).
@Entity
@Table(name = "course_subscriptions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourseSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseSubscriptionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    // Faqat CONFIRMED bo'lgach to'ldiriladi (PENDING so'rovda hali bo'sh).
    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
