package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptHeartbeatServiceTest {

    @Mock
    private AssignmentAttemptRepository assignmentAttemptRepository;

    @InjectMocks
    private AttemptHeartbeatService attemptHeartbeatService;

    @Test
    void heartbeat_updatesDurationOnOwnedAttempt() {
        User pupil = User.builder().id(1L).username("pupil").build();
        AssignmentAttempt attempt = AssignmentAttempt.builder().id(100L).pupil(pupil)
                .startedAt(LocalDateTime.now().minusMinutes(1))
                .lastSync(LocalDateTime.now().minusSeconds(30)).durationSec(0).build();

        when(assignmentAttemptRepository.findByIdAndPupil(100L, pupil)).thenReturn(Optional.of(attempt));

        attemptHeartbeatService.heartbeat(pupil, 100L);

        assertThat(attempt.getDurationSec()).isGreaterThanOrEqualTo(29);
    }

    @Test
    void heartbeat_attemptNotFoundOrNotOwned_throws() {
        User pupil = User.builder().id(1L).username("pupil").build();
        when(assignmentAttemptRepository.findByIdAndPupil(100L, pupil)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attemptHeartbeatService.heartbeat(pupil, 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Attempt not found");
    }
}
