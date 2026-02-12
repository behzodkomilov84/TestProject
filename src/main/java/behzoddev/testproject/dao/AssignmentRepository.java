package behzoddev.testproject.dao;

import behzoddev.testproject.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

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
}
