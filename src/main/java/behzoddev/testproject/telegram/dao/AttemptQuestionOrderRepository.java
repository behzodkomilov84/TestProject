package behzoddev.testproject.telegram.dao;

import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.telegram.entity.AttemptQuestionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptQuestionOrderRepository extends JpaRepository<AttemptQuestionOrder, Long> {

    List<AttemptQuestionOrder> findByAttemptOrderByPosition(
            AssignmentAttempt attempt
    );
}