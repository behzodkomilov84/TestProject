package behzoddev.testproject.dao;

import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssignmentAttemptRepository extends JpaRepository<AssignmentAttempt, Long> {

    Optional<AssignmentAttempt> findByAssignmentIdAndPupilId(Long assignmentId, Long pupilId);

    Optional<AssignmentAttempt> findByIdAndPupil(Long id, User pupil);

}
