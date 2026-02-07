package behzoddev.testproject.dao;

import behzoddev.testproject.entity.TeacherGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<TeacherGroup, Long> {}
