package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.*;
import behzoddev.testproject.mapper.TeacherGroupMapper;
import behzoddev.testproject.mapper.UserMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TeacherService {

    private final TeacherGroupRepository teacherGroupRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeacherGroupMapper teacherGroupMapper;
    private final UserRepository userRepository;
    private final Validation validation;
    private final GroupMemberRepository groupMemberRepository;
    private final UserMapper userMapper;

    @Transactional
    @SneakyThrows
    public void createGroup(User teacher, String name) {

        if (!teacher.getRole().getRoleName().equals("ROLE_ADMIN") && !teacher.getRole().getRoleName().equals("ROLE_OWNER")) {
            throw new AccessDeniedException("Gruppani faqat admin statusidagi foydalanuvchi yarata oladi.");
        }

        TeacherGroup g = TeacherGroup.builder()
                .name(name)
                .teacher(teacher)
                .build();

        teacherGroupRepository.save(g);
    }

    @Transactional(readOnly = true)
    public List<ResponseForGetTeacherGroupDto> getTeacherGroupsByUser(User teacher) {
        List<TeacherGroup> teacherGroupsByUserId =
                teacherGroupRepository.getTeacherGroupsByUserId(teacher.getId());

        return teacherGroupMapper
                .mapTeacherGroupListToResponseForGetTeacherGroupDtoList(teacherGroupsByUserId);
    }

    @Transactional
    public void deleteGroup(Long id) {
        teacherGroupRepository.deleteById(id);
    }

    /* ================= QUESTION SETS ================= */

    @Transactional
    public QuestionSetResponseDto createQuestionSet(User teacher, CreateQuestionSetDto dto) {
        // Получаем вопросы по их ID
        Set<Question> questions = new HashSet<>(questionRepository.findAllById(dto.questionIds()));

        // Создаём набор вопросов
        QuestionSet set = QuestionSet.builder()
                .name(dto.name())
                .teacher(teacher)
                .questions(questions)
                .build();

        questionSetRepository.save(set);

        return new QuestionSetResponseDto(
                set.getId(),
                set.getName(),
                set.getQuestions().size()
        );
    }

    public List<QuestionSetDto> getSets(User teacher) {
        return questionSetRepository.findByTeacher(teacher)
                .stream()
                .map(s -> new QuestionSetDto(
                        s.getId(),
                        s.getName(),
                        s.getQuestions()
                                .stream()
                                .map(Question::getId)
                                .toList()
                ))
                .toList();
    }

    /* ================= PUPILS ================= */
    @Transactional
    public void inviteStudent(Long groupId, Long pupilId) {

        if (groupMemberRepository.existsByGroupIdAndPupilId(groupId, pupilId))
            throw new RuntimeException("Already member");

        if (groupInviteRepository
                .findByGroupIdAndPupilId(groupId, pupilId)
                .isPresent())
            return;

        TeacherGroup group =
                teacherGroupRepository.findById(groupId).orElseThrow();

        User pupil =
                userRepository.findById(pupilId).orElseThrow();

        groupInviteRepository.save(GroupInvite.builder()
                .group(group)
                .pupil(pupil)
                .build());
    }

    public List<GroupStudentRowDto> getGroupStudents(Long groupId) {

        return groupInviteRepository.findByGroupId(groupId)
                .stream()
                .map(i -> new GroupStudentRowDto(
                        i.getId(),
                        i.getPupil().getId(),
                        i.getPupil().getUsername(),
                        i.getStatus()
                ))
                .toList();
    }


    /* ================= ASSIGN ================= */

    //Назначение теста
    @Transactional
    public void assignToGroup(AssignToGroupDto dto) {

        Long setId = dto.setId();
        Long groupId = dto.groupId();

        QuestionSet set = questionSetRepository.findById(setId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "QuestionSet not found: " + setId));

        TeacherGroup group = teacherGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Group not found: " + groupId));

        // опционально — защита от повторного назначения
        boolean exists = assignmentRepository
                .existsByQuestionSetIdAndGroupId(setId, groupId);

        if (exists) {
            throw new IllegalStateException(
                    "This test is already assigned to the group");
        }

        Assignment assignment = Assignment.builder()
                .questionSet(set)
                .group(group)
                .assignedAt(LocalDateTime.now())
                .build();

        assignmentRepository.save(assignment);
    }


    @Transactional
    public void updateGroup(Long groupId, UpdateTeacherGroupDto dto, User teacher) {
        TeacherGroup group = teacherGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        // защита — только владелец может редактировать
        if (!group.getTeacher().getId().equals(teacher.getId())) {
            throw new AccessDeniedException("Not your group");
        }

        validation.textFieldMustNotBeEmpty(dto.name());

        group.setName(dto.name().trim());

    }

    public List<GroupDto> getGroupsForSelect(User teacher) {

        return teacherGroupRepository.getTeacherGroupsByUser(teacher)
                .stream()
                .map(teacherGroupMapper::mapTeacherGroupToGroupDto
                )
                .toList();
    }

    public List<GroupStudentDto> getAllStudentsForGroups() {

        return userRepository
                .findByRole_RoleName("ROLE_USER")
                .stream()
                .map(u -> userMapper.mapUserToGroupStudentDto(u))
                .toList();
    }

}
