package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.dto.teacher.*;
import behzoddev.testproject.entity.*;
import behzoddev.testproject.entity.enums.InviteStatus;
import behzoddev.testproject.mapper.TeacherGroupMapper;
import behzoddev.testproject.mapper.UserMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final UserMapper userMapper;
    private final AssignmentAttemptRepository assignmentAttemptRepository;

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

        TeacherGroup group = teacherGroupRepository.findById(groupId).orElseThrow();

        User pupil = userRepository.findById(pupilId).orElseThrow();

        Optional<GroupInvite> optionalGroupInvite = groupInviteRepository
                .findByGroupIdAndPupilId(groupId, pupilId);

        if (optionalGroupInvite.isPresent()) {
            GroupInvite invite = optionalGroupInvite.get();

            // Если уже PENDING или ACCEPTED — ничего делать не нужно
            if (optionalGroupInvite.get().getStatus() == InviteStatus.PENDING ||
                    optionalGroupInvite.get().getStatus() == InviteStatus.ACCEPTED) {
                return;
            }

            // Если REJECTED — просто обновляем существующий
            optionalGroupInvite.get().setStatus(InviteStatus.PENDING);
            groupInviteRepository.save(invite);

            return;
        }

        // Если записи нет — создаём новую
        groupInviteRepository.save(GroupInvite.builder()
                .group(group)
                .pupil(pupil)
                .status(InviteStatus.PENDING)
                .build());
    }

    @Transactional
    public List<GroupStudentRowDto> getGroupStudents(Long groupId) {

        return groupInviteRepository.findByGroupId(groupId)
                .stream()
                .map(i -> new GroupStudentRowDto(
                        i.getId(),
                        i.getPupil().getId(),
                        i.getPupil().getUsername(),
                        i.getStatus().name()
                ))
                .toList();
    }


    /* ================= ASSIGN ================= */

    //Назначение теста
    @Transactional
    public AssignResultDto assignQuestionSetToStudents(User teacher,
                                                       AssignDto payload) {

        Long setId = payload.setId();
        Long groupId = payload.groupId();
        LocalDateTime dueDate = payload.dueDate();
        List<Long> studentIds = payload.studentIds();

                QuestionSet set = questionSetRepository.findById(setId)
                .orElseThrow(() ->
                        new IllegalArgumentException("QuestionSet not found"));

        TeacherGroup group = teacherGroupRepository.findById(groupId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Group not found"));

        // --- 1. Найти существующих студентов

        List<User> existingStudents =
                userRepository.findAllById(studentIds);

        Set<Long> existingIds = existingStudents.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // --- 2. Найти отсутствующих
        List<Long> missingIds = studentIds.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();

        // --- 3. Проверка уже назначенных
        List<Long> alreadyAssigned = new ArrayList<>();
        List<User> toAssign = new ArrayList<>();

        for (User student : existingStudents) {

            boolean exists =
                    assignmentRepository.existsByQuestionSetIdAndGroupIdAndDueDateAndStudentId(
                            setId,
                            groupId,
                            dueDate,
                            student.getId()
                    );

            if (exists)
                alreadyAssigned.add(student.getId());
            else
                toAssign.add(student);
        }

        // --- 4. Создание назначений

        List<Long> assignedIds = new ArrayList<>();

        for (User student : toAssign) {

            Assignment assignment = Assignment.builder()
                    .questionSet(set)
                    .group(group)
                    .pupil(student)
                    .assignedAt(LocalDateTime.now())
                    .assignedBy(teacher)
                    .dueDate(dueDate)
                    .build();

            assignmentRepository.save(assignment);

            assignedIds.add(student.getId());
        }

        // --- 5. Результат
        return AssignResultDto.builder()
                .assigned(assignedIds)
                .missing(missingIds)
                .alreadyAssigned(alreadyAssigned)
                .build();
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

    @Transactional
    public List<GroupStudentDto> getAllStudentsForGroups() {

        return userRepository
                .findByRole_RoleName("ROLE_USER")
                .stream()
                .map(userMapper::mapUserToGroupStudentDto)
                .toList();
    }

    public List<AssignmentAdminRowDto> getAllAssignments() {

        List<Assignment> assignments =
                assignmentRepository.findAllGroupAssignments();

        return assignments.stream().map(a -> {

            List<User> students = new ArrayList<>(a.getGroup().getPupils());

            List<AssignmentAttempt> attempts =
                    assignmentAttemptRepository.findAllByAssignmentId(a.getId());

            Map<Long, AssignmentAttempt> attemptMap =
                    attempts.stream()
                            .collect(Collectors.toMap(
                                    at -> at.getPupil().getId(),
                                    at -> at
                            ));

            int total = students.size();
            int finished = 0;
            int started = 0;
            int percentSum = 0;

            for (User student : students) {

                AssignmentAttempt at =
                        attemptMap.get(student.getId());

                if (at != null) {

                    if (at.getStartedAt() != null)
                        started++;

                    if (at.getFinishedAt() != null)
                        finished++;

                    percentSum += at.getPercent();
                }
            }

            double avg = finished == 0
                    ? 0
                    : (double) percentSum / finished;

            return new AssignmentAdminRowDto(
                    a.getId(),
                    a.getQuestionSet().getName(),
                    a.getGroup().getName(),
                    a.getAssignedAt(),
                    a.getDueDate(),
                    total,
                    finished,
                    avg
            );

        }).toList();
    }


    public List<AssignmentStudentDetailDto> getAssignmentDetails(Long id) {

        Assignment assignment =
                assignmentRepository.findById(id)
                        .orElseThrow();

        List<User> students =
                new ArrayList<>(assignment.getGroup().getPupils());

        List<AssignmentAttempt> attempts =
                assignmentAttemptRepository.findAllByAssignmentId(id);

        Map<Long, AssignmentAttempt> attemptMap =
                attempts.stream()
                        .collect(Collectors.toMap(
                                at -> at.getPupil().getId(),
                                at -> at
                        ));

        List<AssignmentStudentDetailDto> result = new ArrayList<>();

        for (User student : students) {

            AssignmentAttempt at =
                    attemptMap.get(student.getId());

            String status;
            Integer percent = 0;
            Integer duration = 0;
            LocalDateTime lastActivity = null;

            if (at == null) {
                status = "NEW";
            } else if (at.getFinishedAt() != null) {
                status = "FINISHED";
                percent = at.getPercent();
                duration = at.getDurationSec();
                lastActivity = at.getLastSync();
            } else {
                status = "IN_PROGRESS";
                percent = at.getPercent();
                duration = at.getDurationSec();
                lastActivity = at.getLastSync();
            }

            result.add(new AssignmentStudentDetailDto(
                    student.getId(),
                    student.getUsername(),
                    status,
                    percent,
                    duration,
                    lastActivity
            ));
        }

        return result;
    }

}
