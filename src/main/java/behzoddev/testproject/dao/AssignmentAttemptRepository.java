package behzoddev.testproject.dao;

import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentAttemptRepository extends JpaRepository<AssignmentAttempt, Long> {

    Optional<AssignmentAttempt> findByAssignmentIdAndPupilId(Long assignmentId, Long pupilId);

    Optional<AssignmentAttempt> findByIdAndPupil(Long id, User pupil);

    @Query("""
                select distinct a
                    from AssignmentAttempt a
                    left join fetch a.answers ans
                    left join fetch ans.question
                    left join fetch ans.selectedAnswer
                    where a.assignment.id = :taskId
                    and a.pupil = :pupil
            """)
    Optional<AssignmentAttempt> findFullByTaskIdAndPupil(
            @Param("taskId") Long taskId,
            @Param("pupil") User pupil
    );

    List<AssignmentAttempt> findAllByPupil(User pupil);

    List<AssignmentAttempt> findAllByAssignmentId(Long assignmentId);
}
