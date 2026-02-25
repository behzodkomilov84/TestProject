package behzoddev.testproject.dao;

import behzoddev.testproject.dto.teacher.GroupedAssignmentDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("""
                select case when count(a) > 0 then true else false end
                from Assignment a
                where a.questionSet.id = :setId
                  and a.group.id = :groupId
                  and a.dueDate = :dueDate
                  and a.pupil.id = :studentId
            """)
    boolean existsByQuestionSetIdAndGroupIdAndDueDateAndStudentId(
            @Param("setId") Long setId, @Param("groupId") Long groupId,
            @Param("dueDate") LocalDateTime dueDate, @Param("studentId") Long studentId);

    List<Assignment> findAllByPupil(User pupil);

    @Query("""
                SELECT a FROM Assignment a
                JOIN FETCH a.questionSet
                JOIN FETCH a.group g
                JOIN FETCH g.pupils
                WHERE a.group IS NOT NULL
            """)
    List<Assignment> findAllGroupAssignments();

    @Query("""
                SELECT new behzoddev.testproject.dto.teacher.GroupedAssignmentDto(
                    MIN(a.id),
                    a.questionSet.id,
                    a.group.id,
                    a.assignedBy.id,
                    a.assignedAt,
                    a.dueDate,
                    COUNT(a)
                )
                FROM Assignment a
                WHERE a.group IS NOT NULL
                GROUP BY
                    a.questionSet.id,
                    a.group.id,
                    a.assignedBy.id,
                    a.assignedAt,
                    a.dueDate
                ORDER BY a.assignedAt DESC
            """)
    List<GroupedAssignmentDto> findGroupedAssignments();

    List<Assignment> findByGroup_IdAndQuestionSet_IdAndAssignedAtAndDueDate(
            Long groupId,
            Long setId,
            LocalDateTime assignedAt,
            LocalDateTime dueDate
    );

}
