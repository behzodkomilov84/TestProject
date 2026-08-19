package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.teacher.AssignmentAdminRowDto;
import behzoddev.testproject.dto.teacher.ChatMessageDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentService;
import behzoddev.testproject.service.TeacherService;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Botda "💬 Topshiriq chatlari" — har bir topshiriq uchun umumiy chat
 * (AssignmentService orqali, saytdagi bilan bir xil).
 */
@ExtendWith(MockitoExtension.class)
class TelegramAssignmentChatServiceTest {

    private static final Long CHAT_ID = 950L;

    @Mock
    private TeacherService teacherService;
    @Mock
    private AssignmentService assignmentService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TelegramSessionService sessionService;

    @InjectMocks
    private TelegramAssignmentChatService chatService;

    private User teacher;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).roleName("ROLE_ADMIN").build();
        teacher = User.builder().id(1L).username("teacher1").telegramId(CHAT_ID)
                .roles(new HashSet<>(Set.of(role))).build();
    }

    @Test
    void listAssignments_none_saysEmpty() {
        when(teacherService.getAllAssignments(teacher)).thenReturn(List.of());

        SendMessage msg = chatService.listAssignments(teacher);

        assertThat(msg.getText()).contains("Hozircha topshiriqlar yo'q");
    }

    @Test
    void listAssignments_listsButtons() {
        when(teacherService.getAllAssignments(teacher)).thenReturn(List.of(
                new AssignmentAdminRowDto(1L, "1-mavzu", "10-A", null, null, 20L, 12L, 75.0)
        ));

        SendMessage msg = chatService.listAssignments(teacher);

        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void showChat_setsAwaitingStateAndStoresAssignmentId() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        when(assignmentService.getChatForUser(1L, teacher)).thenReturn(List.of(
                new ChatMessageDto(1L, 1L, "teacher1", "Salom!", "ADMIN", LocalDateTime.now())
        ));

        SendMessage msg = chatService.showChat(CHAT_ID, 1L);

        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_CHAT_MESSAGE);
        verify(sessionService).putTempData(CHAT_ID, "tg_chatAssignmentId", "1");
        assertThat(msg.getText()).contains("Salom!").contains("teacher1");
    }

    @Test
    void showChat_empty_saysNoMessagesYet() {
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        when(assignmentService.getChatForUser(1L, teacher)).thenReturn(List.of());

        SendMessage msg = chatService.showChat(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("Hozircha xabar yo'q");
    }

    @Test
    void sendReply_sendsMessageAndRedisplaysChat() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("tg_chatAssignmentId", "1"));
        when(userRepository.findByTelegramId(CHAT_ID)).thenReturn(Optional.of(teacher));
        when(assignmentService.getChatForUser(1L, teacher)).thenReturn(List.of());

        chatService.sendReply(CHAT_ID, "Yaxshi ishladingiz!");

        verify(assignmentService).sendMessage(1L, teacher.getId(), "Yaxshi ishladingiz!");
    }
}
