package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
