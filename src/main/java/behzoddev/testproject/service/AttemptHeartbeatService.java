package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static behzoddev.testproject.service.AssignmentAttemptService.updateDuration;

@Service
@RequiredArgsConstructor
public class AttemptHeartbeatService {

    private final AssignmentAttemptRepository assignmentAttemptRepository;

    @Transactional
    public void heartbeat(User pupil, Long attemptId) {

        AssignmentAttempt attempt =
                assignmentAttemptRepository.findByIdAndPupil(attemptId, pupil)
                        .orElseThrow(() -> new RuntimeException("Attempt not found"));

        updateDuration(attempt);
    }
}
