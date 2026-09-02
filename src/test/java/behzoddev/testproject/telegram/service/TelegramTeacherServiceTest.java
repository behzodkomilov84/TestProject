package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.GroupInviteRepository;
import behzoddev.testproject.dao.QuestionSetRepository;
import behzoddev.testproject.dao.TeacherGroupRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.teacher.AssignDto;
import behzoddev.testproject.dto.teacher.AssignmentAdminRowDto;
import behzoddev.testproject.dto.teacher.AssignmentStudentDetailDto;
import behzoddev.testproject.dto.teacher.GroupStudentRowDto;
import behzoddev.testproject.dto.teacher.QuestionSetDto;
import behzoddev.testproject.dto.teacher.ResponseForGetTeacherGroupDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.TeacherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Botda ADMIN (o'qituvchi) uchun: 👥 Gruppalarim, 📝 Topshiriq berish,
 * 📈 O'quvchilar natijalari — haqiqiy TeacherService orqali.
 */
@ExtendWith(MockitoExtension.class)
class TelegramTeacherServiceTest {

    private static final Long CHAT_ID = 900L;

    @Mock
    private TeacherService teacherService;
    @Mock
    private TeacherGroupRepository teacherGroupRepository;
    @Mock
    private GroupInviteRepository groupInviteRepository;
    @Mock
    private QuestionSetRepository questionSetRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TelegramSessionService sessionService;
    @Mock
    private TelegramAutoLoginService autoLoginService;

    @InjectMocks
    private TelegramTeacherService telegramTeacherService;

    private User teacher;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).roleName("ROLE_ADMIN").build();
        teacher = User.builder().id(1L).username("teacher1").telegramId(CHAT_ID)
                .roles(new HashSet<>(Set.of(role))).build();
        lenient().when(autoLoginService.buildLoginUrl(any(), any()))
                .thenReturn("https://study-grow.uz/telegram-auto-login?token=stub");
    }

    // ===== Gruppalar =====

    @Test
    void listGroups_none_offersCreateButton() {
        when(teacherService.getTeacherGroupsByUser(teacher)).thenReturn(List.of());

        SendMessage msg = telegramTeacherService.listGroups(teacher);

        assertThat(msg.getText()).contains("hali guruh yo'q");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void listGroups_withGroups_listsButtons() {
        when(teacherService.getTeacherGroupsByUser(teacher))
                .thenReturn(List.of(new ResponseForGetTeacherGroupDto(5L, "10-A")));

        SendMessage msg = telegramTeacherService.listGroups(teacher);

        assertThat(msg.getText()).contains("Gruppalarim");
    }

    @Test
    void viewGroup_showsMembersWithStatusEmoji() {
        TeacherGroup group = TeacherGroup.builder().id(5L).name("10-A").build();
        when(teacherGroupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(teacherService.getGroupStudents(5L)).thenReturn(List.of(
                new GroupStudentRowDto(1L, 10L, "student1", "ACCEPTED"),
                new GroupStudentRowDto(2L, 11L, "student2", "PENDING")
        ));

        SendMessage msg = telegramTeacherService.viewGroup(CHAT_ID, 5L);

        assertThat(msg.getText()).contains("student1").contains("student2").contains("10-A");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void applyGroupName_success_clearsSession() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));

        SendMessage msg = telegramTeacherService.applyGroupName(CHAT_ID, "10-B");

        verify(teacherService).createGroup(teacher, "10-B");
        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("✅").contains("10-B");
    }

    @Test
    void applyGroupName_notAllowed_reportsErrorAndClears() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        doThrow(new AccessDeniedException("Gruppani faqat admin yarata oladi"))
                .when(teacherService).createGroup(any(), any());

        SendMessage msg = telegramTeacherService.applyGroupName(CHAT_ID, "10-B");

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("❌");
    }

    @Test
    void applyInviteUsername_unknownUser_doesNotClearSession() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("tg_inviteGroupId", "5"));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        SendMessage msg = telegramTeacherService.applyInviteUsername(CHAT_ID, "ghost");

        verify(sessionService, never()).clear(CHAT_ID);
        assertThat(msg.getText()).contains("topilmadi");
    }

    @Test
    void applyInviteUsername_notAStudent_rejects() {
        Role ownerRole = Role.builder().id(2L).roleName("ROLE_OWNER").build();
        User notStudent = User.builder().id(2L).username("boss").roles(new HashSet<>(Set.of(ownerRole))).build();
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("tg_inviteGroupId", "5"));
        when(userRepository.findByUsername("boss")).thenReturn(Optional.of(notStudent));

        SendMessage msg = telegramTeacherService.applyInviteUsername(CHAT_ID, "boss");

        assertThat(msg.getText()).contains("o'quvchi (USER) emas");
    }

    @Test
    void applyInviteUsername_validStudent_invitesAndClears() {
        Role studentRole = Role.builder().id(3L).roleName("ROLE_USER").build();
        User student = User.builder().id(10L).username("student1").roles(new HashSet<>(Set.of(studentRole))).build();
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("tg_inviteGroupId", "5"));
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(student));

        SendMessage msg = telegramTeacherService.applyInviteUsername(CHAT_ID, "student1");

        verify(teacherService).inviteStudent(5L, 10L);
        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("✅");
    }

    // ===== Topshiriq berish =====

    @Test
    void startAssignFlow_noGroups_promptsToCreateGroupFirst() {
        when(teacherService.getTeacherGroupsByUser(teacher)).thenReturn(List.of());

        SendMessage msg = telegramTeacherService.startAssignFlow(teacher);

        assertThat(msg.getText()).contains("Avval guruh yarating");
    }

    @Test
    void selectAssignGroup_noSets_promptsToCreateSetOnSite() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        when(teacherService.getSets(teacher)).thenReturn(List.of());

        SendMessage msg = telegramTeacherService.selectAssignGroup(CHAT_ID, 5L);

        assertThat(msg.getText()).contains("savollar paketi");
        assertThat(msg.getText()).doesNotContain("(/teacher)");
        verify(autoLoginService).buildLoginUrl(teacher, "/teacher/builder");
    }

    @Test
    void selectAssignGroup_hasSets_storesGroupIdAndListsSets() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        when(teacherService.getSets(teacher)).thenReturn(List.of(new QuestionSetDto(50L, "1-mavzu", List.of(1L, 2L))));

        SendMessage msg = telegramTeacherService.selectAssignGroup(CHAT_ID, 5L);

        verify(sessionService).putTempData(CHAT_ID, "tg_assignGroupId", "5");
        assertThat(msg.getText()).contains("Savollar paketini tanlang");
    }

    @Test
    void finalizeAssign_success_confirmsAndClears() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of(
                "tg_assignGroupId", "5", "tg_assignSetId", "50"));

        SendMessage msg = telegramTeacherService.finalizeAssign(CHAT_ID, 7);

        verify(teacherService).assignQuestionSetToStudents(eq(teacher), any(AssignDto.class));
        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("✅");
    }

    @Test
    void finalizeAssign_alreadyAssigned_reportsErrorAndClears() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of(
                "tg_assignGroupId", "5", "tg_assignSetId", "50"));
        doThrow(new IllegalStateException("Bu topshiriq bu gruppaga allaqachon yuklangan."))
                .when(teacherService).assignQuestionSetToStudents(any(), any());

        SendMessage msg = telegramTeacherService.finalizeAssign(CHAT_ID, 7);

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("❌").contains("allaqachon");
    }

    // ===== Natijalar =====

    @Test
    void listResults_none_saysEmpty() {
        when(teacherService.getAllAssignments(teacher)).thenReturn(List.of());

        SendMessage msg = telegramTeacherService.listResults(teacher);

        assertThat(msg.getText()).contains("Hozircha topshiriqlar yo'q");
    }

    @Test
    void listResults_listsWithProgress() {
        when(teacherService.getAllAssignments(teacher)).thenReturn(List.of(
                new AssignmentAdminRowDto(1L, "1-mavzu", "10-A", null, null, 20L, 12L, 75.0)
        ));

        SendMessage msg = telegramTeacherService.listResults(teacher);

        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void showResultDetail_formatsPerStudentStatus() {
        when(teacherService.getAssignmentDetails(1L)).thenReturn(List.of(
                new AssignmentStudentDetailDto(10L, "student1", "FINISHED", 80, 120, null),
                new AssignmentStudentDetailDto(11L, "student2", "NEW", 0, 0, null)
        ));

        SendMessage msg = telegramTeacherService.showResultDetail(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("student1").contains("80%").contains("student2");
    }

    // ===== cancel =====

    @Test
    void cancelFlow_clearsSession() {
        SendMessage msg = telegramTeacherService.cancelFlow(CHAT_ID);

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("Bekor qilindi");
    }
}
