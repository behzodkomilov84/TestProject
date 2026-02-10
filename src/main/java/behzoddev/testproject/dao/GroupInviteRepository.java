package behzoddev.testproject.dao;

import behzoddev.testproject.entity.GroupInvite;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    List<GroupInvite> findByGroupId(Long groupId);

    Optional<GroupInvite> findByGroupIdAndPupilId(Long groupId, Long pupilId);

    List<GroupInvite> findByPupil(User pupil);

    @Query("SELECT gi FROM GroupInvite gi JOIN FETCH gi.group WHERE gi.pupil = :pupil")
    List<GroupInvite> findByPupilWithGroup(@Param("pupil") User pupil);
}
