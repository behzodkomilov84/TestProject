package behzoddev.testproject.entity;

import behzoddev.testproject.entity.compositeKey.UserQuestionKey;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "user_question_stats", schema = "test_project")

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class UserQuestionStats {

    @EmbeddedId
    private UserQuestionKey id;

    private int totalAttempts;
    private int correctAttempts;
}
