package behzoddev.testproject.dao;

import behzoddev.testproject.entity.GroupMember;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    void deleteByGroupIdAndPupil(Long groupId, User pupil);

    boolean existsByGroupIdAndPupil(Long groupId, User pupil);

    @Query("""
            from GroupMember gm where gm.pupil=:student
            """)
    List<GroupMember> findByUser(@Param("student") User student);
}
