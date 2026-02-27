package behzoddev.testproject.dao;

import behzoddev.testproject.entity.AssignmentChat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentChatRepository extends JpaRepository<AssignmentChat, Long> {

    List<AssignmentChat> findByAssignmentIdOrderByCreatedAtAsc(Long id);

    @EntityGraph(attributePaths = {"sender", "sender.role"})
    List<AssignmentChat> findByAssignmentIdAndDeletedFalseOrderByCreatedAtAsc(Long assignmentId); //⚡ @EntityGraph — предотвращает N+1 при выводе sender.


}
