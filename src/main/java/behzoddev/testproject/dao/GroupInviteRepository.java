package behzoddev.testproject.dao;

import behzoddev.testproject.entity.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    List<GroupInvite> findByGroupId(Long groupId);

    Optional<GroupInvite> findByGroupIdAndPupilId(Long groupId, Long pupilId);
}
