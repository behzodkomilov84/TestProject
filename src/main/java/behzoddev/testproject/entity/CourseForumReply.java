package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Forum savoliga (CourseForumThread) javob — XOHLAGAN foydalanuvchi
// yozishi mumkin, faqat kurs muallifi emas (foydalanuvchi so'rovi,
// 2026-09-06: "бошқа фойдаланувчилар ҳам бу саволга жавоб бера олсалар").
@Entity
@Table(
        name = "course_forum_replies",
        indexes = @Index(name = "idx_forum_reply_thread", columnList = "thread_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseForumReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_forum_reply_thread"))
    private CourseForumThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_forum_reply_author"))
    private User author;

    @Column(name = "reply_text", nullable = false, length = 4000)
    private String replyText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
