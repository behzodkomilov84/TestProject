package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// "Fikr va takliflar" — sayt bo'yicha (kursga bog'liq emas) ochiq
// fikr-taklif yozuvi (foydalanuvchi so'rovi, 2026-09-06: "Фойдаланувчи
// томонидан фикр ва таклифлар ёзадиган бўлим"). Har bir yozuvga XOHLAGAN
// foydalanuvchi (nafaqat muallif) javob (FeedbackReply) yozishi mumkin —
// CourseForumThread bilan bir xil g'oya, lekin global va "status"i bor.
@Entity
@Table(
        name = "feedback_threads",
        indexes = @Index(name = "idx_feedback_thread_created", columnList = "created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_feedback_thread_author"))
    private User author;

    @Column(name = "feedback_text", nullable = false, length = 4000)
    private String feedbackText;

    // FAQAT OWNER/ADMIN o'zgartira oladi (FeedbackService#updateStatus).
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // "O'chirilganlar savati" g'oyasi bilan bir xil — muallif yoki
    // OWNER/ADMIN o'chirsa, javoblari bilan birga darhol yo'qolib
    // qolmaydi (FeedbackService#deleteThread).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
