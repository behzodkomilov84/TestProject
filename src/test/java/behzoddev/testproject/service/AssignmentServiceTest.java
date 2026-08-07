package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentChatRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.teacher.ChatMessageDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentChat;
import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AssignmentChatRepository assignmentChatRepository;

    @InjectMocks
    private AssignmentService assignmentService;

    private User teacher;
    private TeacherGroup group;
    private Assignment assignment;

    private Assignment freshAssignment() {
        teacher = User.builder().id(1L).username("teacher").roles(new HashSet<>(Set.of(
                Role.builder().id(1L).roleName("ROLE_ADMIN").build()))).build();
        group = TeacherGroup.builder().id(1L).teacher(teacher).name("Guruh").build();
        QuestionSet set = QuestionSet.builder().id(1L).teacher(teacher).name("Set").build();
        return Assignment.builder().id(1L).questionSet(set).group(group).assignedBy(teacher)
                .dueDate(LocalDateTime.now().plusDays(3)).build();
    }

    // ===== extendDue / bulkExtend =====

    @Test
    void extendDue_parsesDateAndUpdatesAssignment() {
        assignment = freshAssignment();
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        assignmentService.extendDue(1L, "2027-01-15 10:30:00");

        assertThat(assignment.getDueDate()).isEqualTo(LocalDateTime.of(2027, 1, 15, 10, 30, 0));
    }

    @Test
    void bulkExtend_updatesAllGivenAssignments() {
        Assignment a1 = freshAssignment();
        Assignment a2 = freshAssignment();
        a2.setId(2L);
        when(assignmentRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a1, a2));

        assignmentService.bulkExtend(List.of(1L, 2L), "2027-02-01 00:00:00");

        assertThat(a1.getDueDate()).isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 0));
        assertThat(a2.getDueDate()).isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 0));
    }

    // ===== reassign / bulkReassign =====

    @Test
    void reassign_createsNewCopyWithSameGroupAndQuestionSet() {
        assignment = freshAssignment();
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        assignmentService.reassign(1L);

        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getGroup()).isEqualTo(assignment.getGroup());
        assertThat(captor.getValue().getQuestionSet()).isEqualTo(assignment.getQuestionSet());
        assertThat(captor.getValue().getId()).isNull(); // yangi qator
    }

    @Test
    void bulkReassign_reassignsEachId() {
        Assignment a1 = freshAssignment();
        Assignment a2 = freshAssignment();
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(a1));
        when(assignmentRepository.findById(2L)).thenReturn(Optional.of(a2));

        assignmentService.bulkReassign(List.of(1L, 2L));

        verify(assignmentRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    // ===== sendMessage / getChat =====

    @Test
    void sendMessage_savesChatEntry() {
        assignment = freshAssignment();
        User sender = User.builder().id(2L).username("pupil").build();
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(2L)).thenReturn(Optional.of(sender));

        assignmentService.sendMessage(1L, 2L, "Salom");

        ArgumentCaptor<AssignmentChat> captor = ArgumentCaptor.forClass(AssignmentChat.class);
        verify(assignmentChatRepository).save(captor.capture());
        assertThat(captor.getValue().getMessageText()).isEqualTo("Salom");
        assertThat(captor.getValue().getSender()).isEqualTo(sender);
    }

    @Test
    void sendMessage_assignmentNotFound_throws() {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.sendMessage(1L, 2L, "Salom"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Assignment not found");
    }

    @Test
    void getChat_mapsMessagesWithHighestDisplayRole() {
        assignment = freshAssignment();
        User owner = User.builder().id(3L).username("owner").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_OWNER").build()))).build();
        AssignmentChat chat = AssignmentChat.builder().id(1L).assignment(assignment).sender(owner)
                .messageText("Salom").createdAt(LocalDateTime.now()).build();

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(assignmentChatRepository.findByAssignmentIdAndDeletedFalseOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(chat));

        List<ChatMessageDto> result = assignmentService.getChat(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("ROLE_OWNER");
    }

    // ===== getChatForUser (kirish nazorati) =====

    @Test
    void getChatForUser_teacher_alwaysAllowed() {
        assignment = freshAssignment();
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(assignmentChatRepository.findByAssignmentIdAndDeletedFalseOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        assignmentService.getChatForUser(1L, teacher);
        // exception yo'q -> ADMIN har doim ruxsatli
    }

    @Test
    void getChatForUser_pupilBelongsToGroup_allowed() {
        assignment = freshAssignment();
        User pupil = User.builder().id(4L).username("pupil").build();
        group.setPupils(new HashSet<>(Set.of(pupil)));

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(assignmentChatRepository.findByAssignmentIdAndDeletedFalseOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());

        assignmentService.getChatForUser(1L, pupil);
        // exception yo'q -> ruxsat berildi
    }

    @Test
    void getChatForUser_pupilNotInGroup_throwsAccessDenied() {
        assignment = freshAssignment();
        User outsider = User.builder().id(5L).username("outsider").build();
        group.setPupils(new HashSet<>());

        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> assignmentService.getChatForUser(1L, outsider))
                .isInstanceOf(AccessDeniedException.class);
    }
}
