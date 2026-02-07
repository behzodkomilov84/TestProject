package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {}
