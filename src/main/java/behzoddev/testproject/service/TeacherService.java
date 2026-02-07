package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final GroupRepository groupRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final AssignmentRepository assignmentRepository;

    @Transactional
    public TeacherGroup createGroup(User teacher, String name) {

        TeacherGroup g = TeacherGroup.builder()
                .name(name)
                .teacher(teacher)
                .build();

        return groupRepository.save(g);
    }

    @Transactional
    public void invitePupil(Long groupId, User pupil) {

        TeacherGroup group = groupRepository.findById(groupId).orElseThrow();

        GroupInvite invite = GroupInvite.builder()
                .group(group)
                .pupil(pupil)
                .accepted(false)
                .build();

        groupInviteRepository.save(invite);
    }

    @Transactional
    public QuestionSet createQuestionSet(
            User teacher,
            String name,
            List<Long> questionIds
    ) {

        List<Question> questions =
                questionRepository.findAllById(questionIds);

        return questionSetRepository.save(
                QuestionSet.builder()
                        .name(name)
                        .teacher(teacher)
                        .questions((Set<Question>) questions)
                        .build()
        );
    }

    //Назначение теста
    @Transactional
    public void assignToGroup(Long setId, Long groupId) {

        QuestionSet set = questionSetRepository.findById(setId).orElseThrow();
        TeacherGroup group = groupRepository.findById(groupId).orElseThrow();

        Assignment assignment = Assignment.builder()
                .questionSet(set)
                .group(group)
                .assignedAt(LocalDateTime.now())
                .build();

        assignmentRepository.save(assignment);
    }

}
