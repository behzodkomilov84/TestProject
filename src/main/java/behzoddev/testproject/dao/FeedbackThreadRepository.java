package behzoddev.testproject.dao;

import behzoddev.testproject.entity.FeedbackThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackThreadRepository extends JpaRepository<FeedbackThread, Long> {

    // "Fikr va takliflar" ro'yxati — eng yangisi tepada (FeedbackService#listThreads).
    List<FeedbackThread> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
