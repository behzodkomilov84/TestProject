package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Foydalanuvchi ma'lum bir bo'limni "tugatgani" (matnni ochgani yoki
// videoni oxirigacha ko'rgani) haqida yozuv — ketma-ket unlock mantiqi
// shu jadvalga asoslanadi: bo'lim N faqat bo'lim N-1 uchun shu yozuv
// mavjud bo'lsa ochiladi.
@Entity
@Table(name = "course_section_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "section_id"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourseSectionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private CourseSection section;

    @Column(name = "completed_at", nullable = false)
    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();
}
