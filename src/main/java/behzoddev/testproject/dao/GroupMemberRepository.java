package behzoddev.testproject.dao;

import behzoddev.testproject.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByGroupIdAndPupilId(Long groupId, Long pupilId);
}
