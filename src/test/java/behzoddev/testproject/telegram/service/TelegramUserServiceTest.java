package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.AssignmentAttemptRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.TelegramLinkCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.PageResponseDto;
import behzoddev.testproject.dto.student.GroupInviteDto;
import behzoddev.testproject.dto.testsession.TestSessionHistoryDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentAttempt;
import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import behzoddev.testproject.service.StudentService;
import behzoddev.testproject.service.SubscriptionService;
import behzoddev.testproject.service.TestSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "📊 Natijalarim" — endi faqat o'qituvchi topshiriqlari (AssignmentAttempt)
 * emas, mustaqil testlar (TestSession, saytdagi va botdagi bir xil
 * TestSessionService orqali) ham birlashtirilib, eng oxirgi 10 tasi
 * ko'rsatiladi. Avval mustaqil test topganlar "hali test topshirmagansiz"
 * degan chalkash xabar olishardi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramUserServiceTest {

    private static final Long CHAT_ID = 555L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TelegramLinkCodeRepository telegramLinkCodeRepository;
    @Mock
    private AssignmentAttemptRepository assignmentAttemptRepository;
    @Mock
    private AssignmentAttemptService assignmentAttemptService;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private TestSessionService testSessionService;
    @Mock
    private StudentService studentService;

    @InjectMocks
    private TelegramUserService telegramUserService;

    @Test
    void sendMyResults_noAssignmentsNoPracticeTests_saysNoneYet() {
        when(assignmentAttemptRepository.findByPupil_TelegramId(CHAT_ID)).thenReturn(List.of());
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.empty());

        SendMessage msg = telegramUserService.sendMyResults(CHAT_ID);

        assertThat(msg.getText()).contains("hali test topshirmagansiz");
    }

    @Test
    void sendMyResults_onlyPracticeTest_showsIt() {
        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();
        when(assignmentAttemptRepository.findByPupil_TelegramId(CHAT_ID)).thenReturn(List.of());
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));

        TestSessionHistoryDto practice = new TestSessionHistoryDto(
                50L, "Matematika", 6, 1, 16, LocalDateTime.now(), 52L);
        when(testSessionService.getHistory(any(), any()))
                .thenReturn(new PageResponseDto<>(List.of(practice), 1, 0, true, true));

        SendMessage msg = telegramUserService.sendMyResults(CHAT_ID);

        assertThat(msg.getText()).contains("Matematika").contains("mustaqil test").contains("16%");
    }

    @Test
    void sendMyResults_mergesAndSortsByMostRecentFirst() {
        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();

        QuestionSet set = QuestionSet.builder().id(5L).name("Fizika-1").build();
        Assignment assignment = Assignment.builder().id(1L).questionSet(set).build();
        AssignmentAttempt olderAttempt = AssignmentAttempt.builder().id(9L)
                .assignment(assignment).percent(70)
                .finishedAt(LocalDateTime.of(2026, 8, 1, 10, 0)).build();
        when(assignmentAttemptRepository.findByPupil_TelegramId(CHAT_ID)).thenReturn(List.of(olderAttempt));
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));

        TestSessionHistoryDto newerPractice = new TestSessionHistoryDto(
                50L, "Matematika", 6, 1, 16, LocalDateTime.of(2026, 8, 19, 15, 0), 52L);
        when(testSessionService.getHistory(any(), any()))
                .thenReturn(new PageResponseDto<>(List.of(newerPractice), 1, 0, true, true));

        SendMessage msg = telegramUserService.sendMyResults(CHAT_ID);

        // Eng yangisi (mustaqil test, 19-avgust) birinchi ko'rsatilishi kerak.
        int practiceIndex = msg.getText().indexOf("Matematika");
        int assignmentIndex = msg.getText().indexOf("Fizika-1");
        assertThat(practiceIndex).isGreaterThanOrEqualTo(0);
        assertThat(assignmentIndex).isGreaterThan(practiceIndex);
    }

    @Test
    void sendMyResults_unfinishedAssignmentAttempt_isExcluded() {
        QuestionSet set = QuestionSet.builder().id(5L).name("Fizika-1").build();
        Assignment assignment = Assignment.builder().id(1L).questionSet(set).build();
        AssignmentAttempt unfinished = AssignmentAttempt.builder().id(9L)
                .assignment(assignment).percent(0).finishedAt(null).build();
        when(assignmentAttemptRepository.findByPupil_TelegramId(CHAT_ID)).thenReturn(List.of(unfinished));
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.empty());

        SendMessage msg = telegramUserService.sendMyResults(CHAT_ID);

        assertThat(msg.getText()).contains("hali test topshirmagansiz");
    }

    // ===== sendMyGroupInvites / respondToGroupInvite =====

    @Test
    void sendMyGroupInvites_noInvites_saysNone() {
        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        when(studentService.getInvites(user)).thenReturn(List.of());

        SendMessage msg = telegramUserService.sendMyGroupInvites(CHAT_ID);

        assertThat(msg.getText()).contains("hozircha guruh taklifi yo'q");
    }

    @Test
    void sendMyGroupInvites_pendingInvite_showsAcceptRejectButtons() {
        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        when(studentService.getInvites(user)).thenReturn(
                List.of(new GroupInviteDto(9L, "Guruh A", "PENDING")));

        SendMessage msg = telegramUserService.sendMyGroupInvites(CHAT_ID);

        assertThat(msg.getText()).contains("Guruh A");
        assertThat(msg.getReplyMarkup()).isNotNull();
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        assertThat(markup.getKeyboard().get(0)).hasSize(2);
        assertThat(markup.getKeyboard().get(0).get(0).getCallbackData()).isEqualTo("group_invite_accept_9");
        assertThat(markup.getKeyboard().get(0).get(1).getCallbackData()).isEqualTo("group_invite_reject_9");
    }

    @Test
    void respondToGroupInvite_accept_delegatesAndShowsUpdatedList() {
        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        when(studentService.getInvites(user)).thenReturn(List.of());

        SendMessage msg = telegramUserService.respondToGroupInvite(CHAT_ID, 9L, true);

        verify(studentService).acceptInvite(9L, user);
        assertThat(msg.getText()).contains("qabul qilindi");
    }

    @Test
    void respondToGroupInvite_serviceThrows_returnsErrorMessage() {
        User user = User.builder().id(1L).username("student").telegramId(CHAT_ID).build();
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new RuntimeException("Bu taklif allaqachon rad etilgan."))
                .when(studentService).rejectInvite(9L, user);

        SendMessage msg = telegramUserService.respondToGroupInvite(CHAT_ID, 9L, false);

        assertThat(msg.getText()).contains("allaqachon rad etilgan");
    }
}
