package behzoddev.testproject.dao;

import behzoddev.testproject.entity.QuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {}
