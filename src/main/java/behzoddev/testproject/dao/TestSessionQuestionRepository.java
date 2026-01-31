package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TestSessionQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSessionQuestionRepository extends JpaRepository<TestSessionQuestion, Long> {

    @EntityGraph(attributePaths = {"question", "selectedAnswer"})
    List<TestSessionQuestion> findByTestSessionId(Long sessionId);
}

