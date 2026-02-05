package behzoddev.testproject.dao;

import behzoddev.testproject.entity.UserQuestionStats;
import behzoddev.testproject.entity.compositeKey.UserQuestionKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserQuestionStatsRepository extends JpaRepository<UserQuestionStats, UserQuestionKey> {
}