package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TeacherGroupRepository extends JpaRepository<TeacherGroup, Long> {

    @Query("""
            select g from TeacherGroup g where g.teacher.id = :userId order by g.name
            """)
    List<TeacherGroup> getTeacherGroupsByUserId(@Param("userId") Long userId);

    @Query("""
            select g from TeacherGroup g where g.teacher = :teacher order by g.name
            """)
    List<TeacherGroup> getTeacherGroupsByUser(@Param("teacher") User teacher);

    @Query("""
    select count(u.id)
    from TeacherGroup g
    join g.pupils u
    where g.id = :groupId
    and u.id in :studentIds
""")
    long countStudentsInGroup(
            @Param("groupId") Long groupId,
            @Param("studentIds") Collection<Long> studentIds
    );

}
