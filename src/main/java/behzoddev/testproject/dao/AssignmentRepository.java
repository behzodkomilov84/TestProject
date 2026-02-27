package behzoddev.testproject.dao;

import behzoddev.testproject.dto.teacher.AssignmentAdminRowDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findAllByRecipientsPupil(User pupil);

    @Query("""
                SELECT new behzoddev.testproject.dto.teacher.AssignmentAdminRowDto(
                    a.id,
                    qs.name,
                    g.name,
                    a.assignedAt,
                    a.dueDate,
                    COUNT(DISTINCT u.id),
                    COUNT(DISTINCT atFinished.id),
                    CAST(COALESCE(AVG(atFinished.percent), 0.0) AS double)
                )
                FROM Assignment a
                JOIN a.questionSet qs
                JOIN a.group g
                JOIN g.pupils u
                LEFT JOIN AssignmentAttempt atFinished
                    ON atFinished.assignment = a
                    AND atFinished.pupil = u
                    AND atFinished.finishedAt IS NOT NULL
                WHERE a.assignedBy.id = :teacherId
                GROUP BY a.id, qs.name, g.name, a.assignedAt, a.dueDate
            """)
    List<AssignmentAdminRowDto> findAllAssignmentsByTeacherId(@Param("teacherId") Long teacherId);

    boolean existsByQuestionSetIdAndGroupIdAndDueDate(
            Long setId,
            Long groupId,
            LocalDateTime dueDate
    );

}
