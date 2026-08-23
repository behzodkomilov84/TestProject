package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

// "Bo'lim" — Fan (Science) ichidagi mavzular guruhi (masalan Kimyo fanida
// "I. UMUMIY KIMYO", "II. ANORGANIK KIMYO"...). Ixtiyoriy oraliq daraja:
// Science 1--* TopicSection 1--* Topic. Topic.section NULL bo'lishi mumkin —
// hali bo'limlarga ajratilmagan fan/mavzular eskicha (bo'limsiz, tekis
// ro'yxat) ishlayveradi.
//
// DIQQAT: bu CourseSection (course_sections — kurs video/matn darslari
// bilan bog'liq, butunlay boshqa funksiya) bilan HECH QANDAY ALOQASI YO'Q —
// nomi ataylab boshqacha tanlangan, aralashtirmaslik uchun.
@Entity
@Table(name = "topic_sections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"science_id", "name"}))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class TopicSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "science_id", nullable = false)
    @ToString.Exclude
    private Science science;

    @Column(nullable = false, length = 255)
    private String name;

    // Fan ichidagi tartib raqami (1, 2, 3, ...) — CourseSection.orderIndex
    // bilan bir xil konvensiya.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "section")
    @ToString.Exclude
    private Set<Topic> topics;
}
