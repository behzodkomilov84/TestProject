package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.GroupInviteRepository;
import behzoddev.testproject.dao.GroupMemberRepository;
import behzoddev.testproject.dao.QuestionSetRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.GroupInvite;
import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.InviteStatus;
import behzoddev.testproject.mapper.GroupInviteMapper;
import behzoddev.testproject.mapper.GroupMemberMapper;
import behzoddev.testproject.mapper.QuestionSetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O'quvchining guruh taklifi bilan ishlashi — holat mashinasi
 * (PENDING -> ACCEPTED/REJECTED) va faqat o'ziga tegishli taklifni
 * boshqara olishi (IDOR himoyasi) asosiy e'tibor markazi.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private GroupInviteRepository groupInviteRepository;
    @Mock
    private GroupInviteMapper groupInviteMapper;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupMemberMapper groupMemberMapper;
    @Mock
    private QuestionSetRepository questionSetRepository;
    @Mock
    private QuestionSetMapper questionSetMapper;

    @InjectMocks
    private StudentService studentService;

    // ===== acceptInvite =====

    @Test
    void acceptInvite_pendingInvite_acceptsAndCreatesMembership() {
        User pupil = User.builder().id(1L).username("pupil").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(pupil).status(InviteStatus.PENDING).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));

        studentService.acceptInvite(9L, pupil);

        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        verify(groupMemberRepository).save(any());
    }

    @Test
    void acceptInvite_rejectedInvite_throws() {
        User pupil = User.builder().id(1L).username("pupil").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(pupil).status(InviteStatus.REJECTED).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> studentService.acceptInvite(9L, pupil))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Rad etilgan");
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void acceptInvite_alreadyAccepted_throws() {
        User pupil = User.builder().id(1L).username("pupil").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(pupil).status(InviteStatus.ACCEPTED).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> studentService.acceptInvite(9L, pupil))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("allaqachon qabul qilingan");
    }

    @Test
    void acceptInvite_belongsToAnotherStudent_throws() {
        User owner = User.builder().id(1L).username("pupil").build();
        User intruder = User.builder().id(2L).username("mallory").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(owner).status(InviteStatus.PENDING).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> studentService.acceptInvite(9L, intruder))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not your invite");
    }

    @Test
    void acceptInvite_notFound_throws() {
        when(groupInviteRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.acceptInvite(9L, User.builder().id(1L).build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invite not found");
    }

    // ===== rejectInvite =====

    @Test
    void rejectInvite_pendingInvite_rejectsAndRemovesMembership() {
        User pupil = User.builder().id(1L).username("pupil").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(pupil).status(InviteStatus.PENDING).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));
        when(groupMemberRepository.existsByGroupIdAndPupil(1L, pupil)).thenReturn(true);

        studentService.rejectInvite(9L, pupil);

        assertThat(invite.getStatus()).isEqualTo(InviteStatus.REJECTED);
        verify(groupMemberRepository).deleteByGroupIdAndPupil(1L, pupil);
    }

    @Test
    void rejectInvite_alreadyRejected_throws() {
        User pupil = User.builder().id(1L).username("pupil").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(pupil).status(InviteStatus.REJECTED).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> studentService.rejectInvite(9L, pupil))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("allaqachon rad etilgan");
    }

    @Test
    void rejectInvite_notAMember_throwsAndDoesNotDelete() {
        User pupil = User.builder().id(1L).username("pupil").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(pupil).status(InviteStatus.PENDING).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));
        when(groupMemberRepository.existsByGroupIdAndPupil(1L, pupil)).thenReturn(false);

        assertThatThrownBy(() -> studentService.rejectInvite(9L, pupil))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("gruppada yo'q");

        verify(groupMemberRepository, never()).deleteByGroupIdAndPupil(any(), any());
    }

    @Test
    void rejectInvite_belongsToAnotherStudent_throws() {
        User owner = User.builder().id(1L).username("pupil").build();
        User intruder = User.builder().id(2L).username("mallory").build();
        TeacherGroup group = TeacherGroup.builder().id(1L).name("Guruh A").build();
        GroupInvite invite = GroupInvite.builder().id(9L).group(group).pupil(owner).status(InviteStatus.PENDING).build();

        when(groupInviteRepository.findById(9L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> studentService.rejectInvite(9L, intruder))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not allowed");
    }

    // ===== getMemberships =====

    @Test
    void getMemberships_userNotFound_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.getMemberships("ghost"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Foydalanuvchi topilmadi");
    }

    @Test
    void getMemberships_noMemberships_throws() {
        User pupil = User.builder().id(1L).username("pupil").build();
        when(userRepository.findByUsername("pupil")).thenReturn(Optional.of(pupil));
        when(groupMemberRepository.findByUser(pupil)).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> studentService.getMemberships("pupil"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("A'zolik topilmadi");
    }
}
