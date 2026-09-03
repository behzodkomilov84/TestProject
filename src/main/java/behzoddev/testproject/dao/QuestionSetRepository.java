package behzoddev.testproject.dao;

import behzoddev.testproject.dto.teacher.QuestionSetAdminRowDto;
import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {

    List<QuestionSet> findByTeacher(User teacher);

    @Query("""
        select distinct qs
        from QuestionSet qs
        left join fetch qs.questions q
        left join fetch q.answers
        where qs.id = :id
    """)
    Optional<QuestionSet> fetchFullById(Long id);

    // "Barcha savol to'plamlari" (FAQAT ROLE_OWNER, TeacherService.getAllSetsForOwner)
    // — har bir o'qituvchi/admin o'z to'plamlarini FAQAT o'zida ko'radi,
    // lekin OWNER hammasini (kim nechta to'plam yaratgani, nechta savol
    // borligi) bitta ro'yxatda ko'rishi kerak.
    @Query("select new behzoddev.testproject.dto.teacher.QuestionSetAdminRowDto(" +
            "qs.id, qs.name, qs.teacher.username, size(qs.questions)) " +
            "from QuestionSet qs order by qs.teacher.username, qs.name")
    List<QuestionSetAdminRowDto> findAllForAdmin();
}
