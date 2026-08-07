package behzoddev.testproject.service;

import behzoddev.testproject.dao.NotificationRepository;
import behzoddev.testproject.dto.notification.NotificationDto;
import behzoddev.testproject.dto.notification.NotificationStatsDto;
import behzoddev.testproject.entity.Notification;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.TelegramBot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saytning bildirishnoma markazi — har bir yozuv Telegram ulangan
 * foydalanuvchiga avtomatik nusxalanadi ("✅ O'qildim" tugmasi bilan),
 * lekin Telegram xatoligi sayt bildirishnomasini bloklamasligi kerak.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TelegramBot telegramBot;

    @InjectMocks
    private NotificationService notificationService;

    // ===== create =====

    @Test
    void create_userWithoutTelegram_savesAndSkipsTelegramSend() throws Exception {
        User user = User.builder().id(1L).username("bob").telegramId(null).build();
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        notificationService.create(user, "Salom", "/profile");

        verify(telegramBot, never()).execute(any(SendMessage.class));
    }

    @Test
    void create_userWithTelegram_sendsCopyWithReadButton() throws Exception {
        User user = User.builder().id(1L).username("bob").telegramId(555L).build();
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(42L);
            return n;
        });

        notificationService.create(user, "Salom", "/profile");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramBot).execute(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo("555");
        assertThat(captor.getValue().getText()).contains("Salom");
        assertThat(captor.getValue().getReplyMarkup()).isNotNull();
    }

    @Test
    void create_telegramSendThrows_stillSucceedsSilently() throws Exception {
        User user = User.builder().id(1L).username("bob").telegramId(555L).build();
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("network")).when(telegramBot).execute(any(SendMessage.class));

        // Exception yutilishi kerak — sayt bildirishnomasi baribir saqlanadi.
        notificationService.create(user, "Salom", "/profile");

        verify(notificationRepository).save(any());
    }

    // ===== list / unreadCount / listByStatus / stats =====

    @Test
    void list_mapsRepositoryResultsToDto() {
        User user = User.builder().id(1L).username("bob").build();
        Notification n = Notification.builder().id(1L).user(user).message("Salom").link("/profile").read(false).build();
        when(notificationRepository.findTop50ByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n));

        List<NotificationDto> result = notificationService.list(user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).message()).isEqualTo("Salom");
        assertThat(result.get(0).read()).isFalse();
    }

    @Test
    void unreadCount_delegatesToRepository() {
        User user = User.builder().id(1L).username("bob").build();
        when(notificationRepository.countByUser_IdAndReadFalse(1L)).thenReturn(3L);

        assertThat(notificationService.unreadCount(user)).isEqualTo(3L);
    }

    @Test
    void listByStatus_read_delegatesWithTrueFlag() {
        User user = User.builder().id(1L).username("bob").build();
        when(notificationRepository.findByUser_IdAndReadOrderByCreatedAtDesc(1L, true)).thenReturn(List.of());

        notificationService.listByStatus(user, true);

        verify(notificationRepository).findByUser_IdAndReadOrderByCreatedAtDesc(1L, true);
    }

    @Test
    void stats_computesTotalsFromUnreadAndRead() {
        User user = User.builder().id(1L).username("bob").build();
        when(notificationRepository.countByUser_IdAndReadFalse(1L)).thenReturn(2L);
        when(notificationRepository.countByUser_IdAndReadTrue(1L)).thenReturn(5L);

        NotificationStatsDto stats = notificationService.stats(user);

        assertThat(stats.unread()).isEqualTo(2L);
        assertThat(stats.read()).isEqualTo(5L);
        assertThat(stats.total()).isEqualTo(7L);
    }

    // ===== markRead =====

    @Test
    void markRead_success_setsReadTrue() {
        User user = User.builder().id(1L).username("bob").build();
        Notification n = Notification.builder().id(9L).user(user).message("m").read(false).build();
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(n));

        notificationService.markRead(9L, user);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void markRead_notFound_throws() {
        when(notificationRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(9L, User.builder().id(1L).build()))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void markRead_belongsToAnotherUser_throwsAccessDenied() {
        User owner = User.builder().id(1L).username("bob").build();
        User intruder = User.builder().id(2L).username("mallory").build();
        Notification n = Notification.builder().id(9L).user(owner).message("m").read(false).build();
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markRead(9L, intruder))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(n.isRead()).isFalse();
    }

    // ===== markAllRead =====

    @Test
    void markAllRead_marksEveryUnreadNotification() {
        User user = User.builder().id(1L).username("bob").build();
        Notification n1 = Notification.builder().id(1L).user(user).message("a").read(false).build();
        Notification n2 = Notification.builder().id(2L).user(user).message("b").read(false).build();
        when(notificationRepository.findByUser_IdAndReadFalse(1L)).thenReturn(List.of(n1, n2));

        notificationService.markAllRead(user);

        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }
}
