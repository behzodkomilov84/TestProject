package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.GroupInviteRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.QuestionSetRepository;
import behzoddev.testproject.dao.TeacherGroupRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.teacher.AssignDto;
import behzoddev.testproject.dto.teacher.AssignResultDto;
import behzoddev.testproject.dto.teacher.AssignmentStudentDetailDto;
import behzoddev.testproject.dto.teacher.CreateQuestionSetDto;
import behzoddev.testproject.dto.teacher.GroupStudentRowDto;
import behzoddev.testproject.dto.teacher.QuestionSetResponseDto;
import behzoddev.testproject.dto.teacher.UpdateTeacherGroupDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.GroupInvite;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.InviteStatus;
import behzoddev.testproject.mapper.TeacherGroupMapper;
import behzoddev.testproject.mapper.UserMapper;
import behzoddev.testproject.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O'qituvchi (ROLE_ADMIN) guruh/topshiriq boshqaruvi — asosiy e'tibor
 * ruxsat tekshiruvlariga (faqat egasi tahrirlashi/o'chirishi mumkin) va
 * taklif holat mashinasiga (PENDING/ACCEPTED/REJECTED).
 */
@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock
    private TeacherGroupRepository teacherGroupRepository;
    @Mock
    private GroupInviteRepository groupInviteRepository;
    @Mock
    private QuestionSetRepository questionSetRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private TeacherGroupMapper teacherGroupMapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Validation validation;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AssignmentAttemptRepository assignmentAttemptRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TeacherService teacherService;

    private User adminRoleUser(long id) {
        return User.builder().id(id).username("teacher" + id).roles(new HashSet<>(Set.of(
                Role.builder().id(1L).roleName("ROLE_ADMIN").build()))).build();
    }

    // ===== createGroup =====

    @Test
    void createGroup_asAdmin_savesGroup() {
        User teacher = adminRoleUser(1L);

        teacherService.createGroup(teacher, "Guruh A");

        verify(teacherGroupRepository).save(org.mockito.ArgumentMatchers.argThat(
                g -> g.getName().equals("Guruh A") && g.getTeacher().equals(teacher)));
    }

    @Test
    void createGroup_plainUser_throwsAccessDenied() {
        User student = User.builder().id(1L).username("student").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_USER").build()))).build();

        assertThatThrownBy(() -> teacherService.createGroup(student, "Guruh A"))
                .isInstanceOf(AccessDeniedException.class);

        verify(teacherGroupRepository, never()).save(any());
    }

    @Test
    void deleteGroup_delegatesToRepository() {
        teacherService.deleteGroup(5L);
        verify(teacherGroupRepository).deleteById(5L);
    }

    // ===== createQuestionSet / getSets =====

    @Test
    void createQuestionSet_success_returnsCountOfFoundQuestions() {
        User teacher = adminRoleUser(1L);
        Question q1 = Question.builder().id(1L).questionText("Q1").build();
        Question q2 = Question.builder().id(2L).questionText("Q2").build();
        CreateQuestionSetDto dto = new CreateQuestionSetDto("Set1", Set.of(1L, 2L));

        when(questionRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(q1, q2));

        QuestionSetResponseDto result = teacherService.createQuestionSet(teacher, dto);

        assertThat(result.name()).isEqualTo("Set1");
        assertThat(result.questionCount()).isEqualTo(2);
    }

    // ===== renameQuestionSet / deleteQuestionSet ("Savollar to'plami" sahifasi) =====

    @Test
    void renameQuestionSet_owner_updatesName() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = QuestionSet.builder().id(1L).teacher(teacher).name("Eski nom").build();
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));

        teacherService.renameQuestionSet(1L, "Yangi nom", teacher);

        assertThat(set.getName()).isEqualTo("Yangi nom");
    }

    @Test
    void renameQuestionSet_notOwner_throwsAccessDenied() {
        User owner = adminRoleUser(1L);
        User other = adminRoleUser(2L);
        QuestionSet set = QuestionSet.builder().id(1L).teacher(owner).name("Set1").build();
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> teacherService.renameQuestionSet(1L, "Yangi nom", other))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteQuestionSet_owner_notUsedInAssignment_deletes() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = QuestionSet.builder().id(1L).teacher(teacher).name("Set1").build();
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(assignmentRepository.existsByQuestionSetId(1L)).thenReturn(false);

        teacherService.deleteQuestionSet(1L, teacher);

        verify(questionSetRepository).delete(set);
    }

    @Test
    void deleteQuestionSet_alreadyAssigned_throwsAndDoesNotDelete() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = QuestionSet.builder().id(1L).teacher(teacher).name("Set1").build();
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(assignmentRepository.existsByQuestionSetId(1L)).thenReturn(true);

        assertThatThrownBy(() -> teacherService.deleteQuestionSet(1L, teacher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topshiriq");

        verify(questionSetRepository, never()).delete(any());
    }

    @Test
    void deleteQuestionSet_notOwner_throwsAccessDenied() {
        User owner = adminRoleUser(1L);
        User other = adminRoleUser(2L);
        QuestionSet set = QuestionSet.builder().id(1L).teacher(owner).name("Set1").build();
        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> teacherService.deleteQuestionSet(1L, other))
                .isInstanceOf(AccessDeniedException.class);

        verify(questionSetRepository, never()).delete(any());
    }

    // ===== inviteStudent (holat mashinasi) =====

    @Test
    void inviteStudent_noExistingInvite_createsPendingAndNotifies() {
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();

        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pupil));
        when(groupInviteRepository.findByGroupIdAndPupilId(1L, 2L)).thenReturn(Optional.empty());

        teacherService.inviteStudent(1L, 2L);

        verify(groupInviteRepository).save(org.mockito.ArgumentMatchers.argThat(
                inv -> inv.getStatus() == InviteStatus.PENDING));
        verify(notificationService).create(eq(pupil), anyString(), anyString());
    }

    @Test
    void inviteStudent_existingPending_doesNothing() {
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();
        GroupInvite existing = GroupInvite.builder().id(9L).group(group).pupil(pupil)
                .status(InviteStatus.PENDING).build();

        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pupil));
        when(groupInviteRepository.findByGroupIdAndPupilId(1L, 2L)).thenReturn(Optional.of(existing));

        teacherService.inviteStudent(1L, 2L);

        verify(groupInviteRepository, never()).save(any());
        verify(notificationService, never()).create(any(), anyString(), anyString());
    }

    @Test
    void inviteStudent_existingAccepted_doesNothing() {
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();
        GroupInvite existing = GroupInvite.builder().id(9L).group(group).pupil(pupil)
                .status(InviteStatus.ACCEPTED).build();

        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pupil));
        when(groupInviteRepository.findByGroupIdAndPupilId(1L, 2L)).thenReturn(Optional.of(existing));

        teacherService.inviteStudent(1L, 2L);

        verify(groupInviteRepository, never()).save(any());
    }

    @Test
    void inviteStudent_existingRejected_resetsToPendingAndNotifiesAgain() {
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();
        GroupInvite existing = GroupInvite.builder().id(9L).group(group).pupil(pupil)
                .status(InviteStatus.REJECTED).build();

        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pupil));
        when(groupInviteRepository.findByGroupIdAndPupilId(1L, 2L)).thenReturn(Optional.of(existing));

        teacherService.inviteStudent(1L, 2L);

        assertThat(existing.getStatus()).isEqualTo(InviteStatus.PENDING);
        verify(groupInviteRepository).save(existing);
        verify(notificationService).create(eq(pupil), anyString(), anyString());
    }

    @Test
    void getGroupStudents_mapsInvitesToRows() {
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(pupil).status(InviteStatus.PENDING).build();
        when(groupInviteRepository.findByGroupId(1L)).thenReturn(List.of(invite));

        List<GroupStudentRowDto> result = teacherService.getGroupStudents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("pupil");
        assertThat(result.get(0).status()).isEqualTo("PENDING");
    }

    // ===== assignQuestionSetToStudents =====

    private QuestionSet freshSet(User teacher) {
        return QuestionSet.builder().id(1L).teacher(teacher).name("Set1").build();
    }

    @Test
    void assignQuestionSetToStudents_wholeGroup_success() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = freshSet(teacher);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();
        LocalDateTime due = LocalDateTime.now().plusDays(5);

        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(assignmentRepository.existsByQuestionSetIdAndGroupIdAndDueDate(1L, 1L, due)).thenReturn(false);
        when(userRepository.findAllByGroupId(1L)).thenReturn(List.of(pupil));

        AssignDto dto = new AssignDto(1L, 1L, due, null);
        AssignResultDto result = teacherService.assignQuestionSetToStudents(teacher, dto);

        assertThat(result.assigned()).containsExactly(2L);
        verify(assignmentRepository).save(any());
        verify(notificationService).create(eq(pupil), anyString(), anyString());
    }

    @Test
    void assignQuestionSetToStudents_specificStudents_success() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = freshSet(teacher);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();
        LocalDateTime due = LocalDateTime.now().plusDays(5);

        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(assignmentRepository.existsByQuestionSetIdAndGroupIdAndDueDate(1L, 1L, due)).thenReturn(false);
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(pupil));
        when(teacherGroupRepository.countStudentsInGroup(1L, List.of(2L))).thenReturn(1L);

        AssignDto dto = new AssignDto(1L, 1L, due, List.of(2L));
        AssignResultDto result = teacherService.assignQuestionSetToStudents(teacher, dto);

        assertThat(result.assigned()).containsExactly(2L);
    }

    @Test
    void assignQuestionSetToStudents_studentNotFound_throws() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = freshSet(teacher);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh A").build();
        LocalDateTime due = LocalDateTime.now().plusDays(5);

        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(assignmentRepository.existsByQuestionSetIdAndGroupIdAndDueDate(1L, 1L, due)).thenReturn(false);
        when(userRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of());

        AssignDto dto = new AssignDto(1L, 1L, due, List.of(2L, 3L));

        assertThatThrownBy(() -> teacherService.assignQuestionSetToStudents(teacher, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Students not found");
    }

    @Test
    void assignQuestionSetToStudents_studentNotInGroup_throws() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = freshSet(teacher);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh A").build();
        User pupil = User.builder().id(2L).username("pupil").build();
        LocalDateTime due = LocalDateTime.now().plusDays(5);

        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(assignmentRepository.existsByQuestionSetIdAndGroupIdAndDueDate(1L, 1L, due)).thenReturn(false);
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(pupil));
        when(teacherGroupRepository.countStudentsInGroup(1L, List.of(2L))).thenReturn(0L);

        AssignDto dto = new AssignDto(1L, 1L, due, List.of(2L));

        assertThatThrownBy(() -> teacherService.assignQuestionSetToStudents(teacher, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Some students not in this group");
    }

    @Test
    void assignQuestionSetToStudents_notGroupOwner_throws() {
        User teacher = adminRoleUser(1L);
        User otherTeacher = adminRoleUser(99L);
        QuestionSet set = freshSet(teacher);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(otherTeacher).name("Guruh A").build();

        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));

        AssignDto dto = new AssignDto(1L, 1L, LocalDateTime.now().plusDays(1), null);

        assertThatThrownBy(() -> teacherService.assignQuestionSetToStudents(teacher, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Begona gruppaga");
    }

    @Test
    void assignQuestionSetToStudents_duplicateAssignment_throws() {
        User teacher = adminRoleUser(1L);
        QuestionSet set = freshSet(teacher);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh A").build();
        LocalDateTime due = LocalDateTime.now().plusDays(5);

        when(questionSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(assignmentRepository.existsByQuestionSetIdAndGroupIdAndDueDate(1L, 1L, due)).thenReturn(true);

        AssignDto dto = new AssignDto(1L, 1L, due, null);

        assertThatThrownBy(() -> teacherService.assignQuestionSetToStudents(teacher, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allaqachon yuklangan");
    }

    @Test
    void assignQuestionSetToStudents_setNotFound_throws() {
        when(questionSetRepository.findById(1L)).thenReturn(Optional.empty());
        AssignDto dto = new AssignDto(1L, 1L, LocalDateTime.now(), null);

        assertThatThrownBy(() -> teacherService.assignQuestionSetToStudents(adminRoleUser(1L), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Savollar paketi topilmadi");
    }

    // ===== updateGroup =====

    @Test
    void updateGroup_owner_updatesName() {
        User teacher = adminRoleUser(1L);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Eski").build();
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));

        teacherService.updateGroup(1L, new UpdateTeacherGroupDto("Yangi"), teacher);

        assertThat(group.getName()).isEqualTo("Yangi");
        verify(validation).textFieldMustNotBeEmpty("Yangi");
    }

    @Test
    void updateGroup_notOwner_throwsAccessDenied() {
        User teacher = adminRoleUser(1L);
        User other = adminRoleUser(2L);
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Eski").build();
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> teacherService.updateGroup(1L, new UpdateTeacherGroupDto("Yangi"), other))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateGroup_notFound_throws() {
        when(teacherGroupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.updateGroup(1L, new UpdateTeacherGroupDto("X"), adminRoleUser(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Group not found");
    }

    // ===== getAssignmentDetails =====

    @Test
    void getAssignmentDetails_mapsStatusesForEachStudent() {
        User teacher = adminRoleUser(1L);
        User newPupil = User.builder().id(2L).username("new_pupil").build();
        User finishedPupil = User.builder().id(3L).username("finished_pupil").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh A")
                .pupils(new HashSet<>(Set.of(newPupil, finishedPupil))).build();
        Assignment assignment = Assignment.builder().id(1L).group(group).assignedBy(teacher).build();

        AssignmentAttempt finishedAttempt = AssignmentAttempt.builder().id(10L).pupil(finishedPupil)
                .finishedAt(LocalDateTime.now()).percent(90).durationSec(120).build();

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(assignmentAttemptRepository.findAllByAssignmentId(1L)).thenReturn(List.of(finishedAttempt));

        List<AssignmentStudentDetailDto> result = teacherService.getAssignmentDetails(1L);

        assertThat(result).hasSize(2);
        AssignmentStudentDetailDto newResult = result.stream().filter(r -> r.pupilId().equals(2L)).findFirst().orElseThrow();
        AssignmentStudentDetailDto finishedResult = result.stream().filter(r -> r.pupilId().equals(3L)).findFirst().orElseThrow();

        assertThat(newResult.status()).isEqualTo("NEW");
        assertThat(finishedResult.status()).isEqualTo("FINISHED");
        assertThat(finishedResult.percent()).isEqualTo(90);
    }

    // ===== bulkDeleteAssignments =====

    @Test
    void bulkDeleteAssignments_ownAssignments_deletesAll() {
        User teacher = adminRoleUser(1L);
        Assignment a1 = Assignment.builder().id(1L).assignedBy(teacher).build();
        Assignment a2 = Assignment.builder().id(2L).assignedBy(teacher).build();
        when(assignmentRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a1, a2));

        teacherService.bulkDeleteAssignments(List.of(1L, 2L), teacher);

        verify(assignmentRepository).deleteAll(List.of(a1, a2));
    }

    @Test
    void bulkDeleteAssignments_notOwnedAssignment_throwsAndDeletesNothing() {
        User teacher = adminRoleUser(1L);
        User otherTeacher = adminRoleUser(2L);
        Assignment foreign = Assignment.builder().id(1L).assignedBy(otherTeacher).build();
        when(assignmentRepository.findAllById(List.of(1L))).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> teacherService.bulkDeleteAssignments(List.of(1L), teacher))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("O'zingiz qo'ymagan");

        verify(assignmentRepository, never()).deleteAll(any());
    }
}
