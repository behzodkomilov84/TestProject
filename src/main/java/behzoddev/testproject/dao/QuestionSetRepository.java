package behzoddev.testproject.dao;

import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {

    List<QuestionSet> findByTeacher(User teacher);
}
