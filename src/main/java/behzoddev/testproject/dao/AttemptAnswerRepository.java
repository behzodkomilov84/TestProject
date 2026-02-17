package behzoddev.testproject.dao;

import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    List<AttemptAnswer> findByAssignmentAttempt(AssignmentAttempt attempt);
}
