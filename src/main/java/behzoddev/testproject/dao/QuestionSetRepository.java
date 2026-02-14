package behzoddev.testproject.dao;

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
}
