package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.User;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AttemptHeartbeatService {

    private final AssignmentAttemptRepository assignmentAttemptRepository;

    @Transactional
    public void heartbeat(User pupil, Long attemptId) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository.findByIdAndPupil(attemptId, pupil)
                        .orElseThrow();

        // 🔥 FINISHED — STOP
        if (attempt.getFinishedAt() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (attempt.getLastSync() != null) {

            long delta =
                    Duration.between(
                            attempt.getLastSync(),
                            now
                    ).getSeconds();

            // защита от накрутки
            if (delta > 0 && delta < 15) {
                attempt.setDurationSec(
                        attempt.getDurationSec() + (int) delta
                );
            }
        }

        attempt.setLastSync(now);
    }
}
