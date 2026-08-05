package behzoddev.testproject.entity;

import behzoddev.testproject.entity.enums.CourseSectionType;
import behzoddev.testproject.entity.enums.VideoSourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Kursning bitta bo'limi (mavzu/lesson) — matn YOKI video ko'rinishida
// bo'ladi. Bo'limlar orderIndex bo'yicha ketma-ket ochiladi (1-bo'lim
// obunadan keyin darhol, keyingilari oldingisi tugatilgach).
@Entity
@Table(name = "course_sections")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourseSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    // Kurs ichidagi tartib raqami (1, 2, 3, ...) — ketma-ket ochilish shu bo'yicha.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CourseSectionType type;

    // type=TEXT bo'lsa to'ldiriladi.
    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    // type=VIDEO bo'lsa quyidagilar to'ldiriladi.
    @Enumerated(EnumType.STRING)
    @Column(name = "video_source_type", length = 10)
    private VideoSourceType videoSourceType;

    // UPLOAD — FileStorageService qaytargan "/uploads/..." yo'l.
    // YOUTUBE — video ID (embed uchun).
    // EXTERNAL — to'liq embed URL.
    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    // Faqat EXTERNAL manba uchun — aniq "tugadi" hodisasi yo'qligi sababli,
    // shu vaqt o'tgach avtomatik "ko'rilgan" deb belgilanadi.
    @Column(name = "video_duration_seconds")
    private Integer videoDurationSeconds;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
