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
    private final NotificationService notificationService;

    @Transactional
    @SneakyThrows
    public void createGroup(User teacher, String name) {

        if (!teacher.hasRole("ROLE_ADMIN") && !teacher.hasRole("ROLE_OWNER")) {
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

    // "✏️" — savollar paketi nomini o'zgartirish ("Savollar to'plami"
    // sahifasi) — updateGroup bilan bir xil andoza: faqat egasi (teacher).
    // "Avtomatik tanlash" — "Savollar to'plami" sahifasida bir nechta
    // Bo'lim/Mavzu belgilab, jami nechta savol kerakligini kiritsa, HAR
    // BIR MAVZUGA TENG bo'lib (savollar soniga qarab EMAS — "Variantlar
    // yaratish" bilan bir xil "suv quyish" algoritmi, QuestionAllocationUtil)
    // tasodifiy savollarni tanlab beradi. Natija — oddiy ro'yxat, hech
    // narsa avtomatik SAQLANMAYDI (o'qituvchi ko'rib chiqib, xohlasa
    // qo'shimcha qo'shishi/olib tashlashi, keyin nomini kiritib
    // "Saqlash"ni bosishi kerak — mavjud oqim o'zgarishsiz).
    @Transactional(readOnly = true)
    public List<ResponseQuestionTextDto> autoSelectQuestions(List<Long> topicIds, int totalCount) {
        if (topicIds == null || topicIds.isEmpty()) {
            throw new IllegalArgumentException("❌ Kamida bitta mavzu tanlang.");
        }
        if (totalCount < 1) {
            throw new IllegalArgumentException("❌ Jami savollar soni kamida 1 bo'lishi kerak.");
        }

        Map<Long, List<Question>> questionsByTopic = new LinkedHashMap<>();
        for (Long topicId : topicIds) {
            questionsByTopic.put(topicId, questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(topicId));
        }

        Map<Long, Integer> capacity = new LinkedHashMap<>();
        questionsByTopic.forEach((id, list) -> capacity.put(id, list.size()));

        Random random = new Random();
        Map<Long, Integer> allocation = QuestionAllocationUtil.allocateEqually(topicIds, capacity, totalCount, random);

        List<Question> selected = new ArrayList<>();
        for (Long topicId : topicIds) {
            List<Question> pool = new ArrayList<>(questionsByTopic.get(topicId));
            Collections.shuffle(pool, random);
            int take = allocation.getOrDefault(topicId, 0);
            selected.addAll(pool.subList(0, Math.min(take, pool.size())));
        }
        Collections.shuffle(selected, random);

        return selected.stream()
                .map(q -> new ResponseQuestionTextDto(q.getId(), q.getQuestionText()))
                .toList();
    }

    @Transactional
    public void renameQuestionSet(Long setId, String newName, User teacher) {
        QuestionSet set = questionSetRepository.findById(setId)
                .orElseThrow(() -> new IllegalArgumentException("Savollar paketi topilmadi."));

        if (!set.getTeacher().getId().equals(teacher.getId())) {
            throw new AccessDeniedException("Bu sizning paketingiz emas.");
        }

        validation.textFieldMustNotBeEmpty(newName);
        set.setName(newName.trim());
    }

    // "🗑️" — savollar paketini o'chirish. Allaqachon biror topshiriqda
    // ishlatilgan bo'lsa o'chirib bo'lmaydi (Assignment.questionSet FK —
    // aks holda bazada RESTRICT xatoligiga uchraydi, bu yerda oldindan,
    // tushunarli xabar bilan bloklanadi).
    @Transactional
    public void deleteQuestionSet(Long setId, User teacher) {
        QuestionSet set = questionSetRepository.findById(setId)
                .orElseThrow(() -> new IllegalArgumentException("Savollar paketi topilmadi."));

        if (!set.getTeacher().getId().equals(teacher.getId())) {
            throw new AccessDeniedException("Bu sizning paketingiz emas.");
        }

        if (assignmentRepository.existsByQuestionSetId(setId)) {
            throw new IllegalArgumentException(
                    "❌ Bu paket allaqachon topshiriq sifatida berilgan — avval topshiriqni o'chiring.");
        }

        questionSetRepository.delete(set);
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

            notificationService.create(pupil,
                    "👥 Siz \"" + group.getName() + "\" guruhiga taklif qilindingiz.",
                    "/student?tab=invites");

            return;
        }

        // Если записи нет — создаём новую
        groupInviteRepository.save(GroupInvite.builder()
                .group(group)
                .pupil(pupil)
                .status(InviteStatus.PENDING)
                .build());

        notificationService.create(pupil,
                "👥 Siz \"" + group.getName() + "\" guruhiga taklif qilindingiz.",
                "/student");
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

        // --- 1. Проверка QuestionSet

        QuestionSet set = questionSetRepository.findById(setId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Savollar paketi topilmadi."));

        // --- 2. Проверка группы

        TeacherGroup group = teacherGroupRepository.findById(groupId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Gruppa topilmadi."));

        // --- 3. Проверка что учитель владелец группы

        if (!group.getTeacher().getId().equals(teacher.getId())) {
            throw new IllegalStateException("Begona gruppaga topshiriq berolmaysiz.");
        }

        // --- 4. Проверка уже существующего assignment (одно на группу)

        boolean alreadyExists =
                assignmentRepository.existsByQuestionSetIdAndGroupIdAndDueDate(
                        setId,
                        groupId,
                        dueDate
                );

        if (alreadyExists) {
            throw new IllegalStateException("Bu topshiriq bu gruppaga allaqachon yuklangan.");
        }

        // --- 5. Определяем список студентов

        List<User> pupils;

        if (studentIds == null || studentIds.isEmpty()) {
            // назначить всем в группе
            pupils = userRepository.findAllByGroupId(groupId);
        } else {

            pupils = userRepository.findAllById(studentIds);

            Set<Long> foundIds = pupils.stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());

            List<Long> missingIds = studentIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();

            if (!missingIds.isEmpty()) {
                throw new IllegalArgumentException("Students not found: " + missingIds);
            }

            long count = teacherGroupRepository
                    .countStudentsInGroup(groupId, studentIds);

            if (count != studentIds.size()) {
                throw new IllegalStateException("Some students not in this group");
            }

        }

        // --- 6. Создаём ОДИН Assignment

        Assignment assignment = Assignment.builder()
                .questionSet(set)
                .group(group)
                .assignedBy(teacher)
                .assignedAt(LocalDateTime.now())
                .dueDate(dueDate)
                .build();

        // --- 7. Создаём AssignmentStudent

        for (User pupil : pupils) {

            AssignmentRecipient assignmentRecipient = AssignmentRecipient.builder()
                    .assignment(assignment)
                    .pupil(pupil)
                    .build();

            assignment.getRecipients().add(assignmentRecipient);
        }

        assignmentRepository.save(assignment);

       //Уведомление когда учитель назначает Assignment
        notifyStudents(assignment);

        // --- 8. Результат

        return AssignResultDto.builder()
                .assigned(
                        pupils.stream()
                                .map(User::getId)
                                .toList()
                )
                .build();
    }

    // Telegram'ga yuborish endi NotificationService.create() ichida
    // avtomatik amalga oshadi (foydalanuvchi botga ulangan bo'lsa).
    private void notifyStudents(Assignment assignment) {

        for (AssignmentRecipient r : assignment.getRecipients()) {

            notificationService.create(r.getPupil(),
                    "📢 Yangi topshiriq: " + assignment.getQuestionSet().getName() +
                            ". Muddat: " + assignment.getDueDate(),
                    "/student?tab=tasks&assignmentId=" + assignment.getId());
        }
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
                .findByRoles_RoleName("ROLE_USER")
                .stream()
                .map(userMapper::mapUserToGroupStudentDto)
                .toList();
    }

    /*public List<AssignmentAdminRowDto> getAllAssignments(User teacher) {

        List<Assignment> assignments =
                assignmentRepository.findAllGroupAssignments(teacher.getId());

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

            Long total = students.size();
            Long finished = 0L;
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

            Double avg = finished == 0
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
    }*/
    public List<AssignmentAdminRowDto> getAllAssignments(User teacher) {
        return assignmentRepository.findAllAssignmentsByTeacherId(teacher.getId());
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

    @Transactional
    public void bulkDeleteAssignments(List<Long> ids, User teacher) {

        List<Assignment> assignments =
                assignmentRepository.findAllById(ids);

        for (Assignment a : assignments) {

            if (!a.getAssignedBy().getId().equals(teacher.getId())) {
                throw new RuntimeException("O'zingiz qo'ymagan topshiriqni o'chira olmaysiz.");
            }
        }

        assignmentRepository.deleteAll(assignments);
    }
}
