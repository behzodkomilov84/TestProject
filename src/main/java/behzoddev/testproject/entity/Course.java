package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// JavaRush uslubidagi online kurs — OWNER tomonidan yaratiladi, ADMIN/USER
// obuna orqali kirish huquqini sotib oladi (CourseSubscription).
@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    // false = qoralama (faqat OWNER ko'radi), true = chop etilgan (katalogda hammaga ko'rinadi).
    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    // true bo'lsa — obunasiz ham (site'da HAM, Telegram bot'da HAM) hammaga
    // to'liq ochiq (CourseService.isSubscribed() shuni tekshiradi).
    @Column(nullable = false)
    @Builder.Default
    private boolean free = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
