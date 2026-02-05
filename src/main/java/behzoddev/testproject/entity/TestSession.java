package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "test_sessions")

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString(exclude = {"user", "questions"})
public class TestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    private Integer totalQuestions;
    private Integer correctAnswers;
    private Integer wrongAnswers;

    private Integer percent;

    private Long durationSec;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "testSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestSessionQuestion> questions;

    public void addQuestion(TestSessionQuestion question) {
        questions.add(question);
        question.setTestSession(this);
    }

}

