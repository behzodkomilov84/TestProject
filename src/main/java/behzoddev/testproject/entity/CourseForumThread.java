package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// "Forum" — kurs foydalanuvchisi kurs muallifiga (yoki umuman kursga)
// beradigan savoli (foydalanuvchi so'rovi, 2026-09-06). Har bir savolga
// XOHLAGAN foydalanuvchi (nafaqat muallif) javob (CourseForumReply)
// yozishi mumkin — haqiqiy forum/Q&A taxtasi kabi.
@Entity
@Table(
        name = "course_forum_threads",
        indexes = @Index(name = "idx_forum_thread_course", columnList = "course_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseForumThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_forum_thread_course"))
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_forum_thread_author"))
    private User author;

    @Column(name = "question_text", nullable = false, length = 4000)
    private String questionText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // "O'chirilganlar savati" g'oyasi bilan bir xil — muallif yoki kurs
    // egasi o'chirsa, javoblari bilan birga darhol yo'qolib qolmaydi
    // (CourseForumService#deleteThread, hozircha faqat muallif/canManage
    // uchun ochiq).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
