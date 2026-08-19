package behzoddev.testproject.telegram.service;

import behzoddev.testproject.telegram.dao.TelegramSessionRepository;
import behzoddev.testproject.telegram.entity.TelegramSession;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TelegramSession — botdagi ko'p bosqichli suhbat holati (masalan profil
 * tahrirlash). Bazada saqlanishi (xotirada emas) muhim — production tez-tez
 * qayta ishga tushadi, xotiradagi holat har safar yo'qolib ketardi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramSessionServiceTest {

    @Mock
    private TelegramSessionRepository telegramSessionRepository;

    private TelegramSessionService sessionService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        sessionService = new TelegramSessionService(telegramSessionRepository, objectMapper);
    }

    @Test
    void getState_noSessionYet_returnsNone() {
        when(telegramSessionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(sessionService.getState(1L)).isEqualTo(BotState.NONE);
    }

    @Test
    void getState_existingSession_returnsStoredState() {
        TelegramSession session = new TelegramSession();
        session.setChatId(1L);
        session.setState(BotState.AWAITING_EMAIL.name());
        when(telegramSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThat(sessionService.getState(1L)).isEqualTo(BotState.AWAITING_EMAIL);
    }

    @Test
    void setState_newChat_createsSessionWithGivenState() {
        when(telegramSessionRepository.findById(1L)).thenReturn(Optional.empty());

        sessionService.setState(1L, BotState.AWAITING_USERNAME);

        ArgumentCaptor<TelegramSession> captor = ArgumentCaptor.forClass(TelegramSession.class);
        verify(telegramSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo(1L);
        assertThat(captor.getValue().getState()).isEqualTo("AWAITING_USERNAME");
    }

    @Test
    void putTempData_mergesWithExistingData() {
        TelegramSession session = new TelegramSession();
        session.setChatId(1L);
        session.setState(BotState.AWAITING_NEW_PASSWORD.name());
        session.setTempData("{\"currentPassword\":\"old123\"}");
        when(telegramSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.putTempData(1L, "extra", "value");

        ArgumentCaptor<TelegramSession> captor = ArgumentCaptor.forClass(TelegramSession.class);
        verify(telegramSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getTempData()).contains("currentPassword").contains("extra");
    }

    @Test
    void getTempData_noData_returnsEmptyMap() {
        when(telegramSessionRepository.findById(1L)).thenReturn(Optional.empty());

        Map<String, String> data = sessionService.getTempData(1L);

        assertThat(data).isEmpty();
    }

    @Test
    void clear_resetsStateToNoneAndWipesTempData() {
        TelegramSession session = new TelegramSession();
        session.setChatId(1L);
        session.setState(BotState.AWAITING_PHONE.name());
        session.setTempData("{\"x\":\"y\"}");
        when(telegramSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.clear(1L);

        ArgumentCaptor<TelegramSession> captor = ArgumentCaptor.forClass(TelegramSession.class);
        verify(telegramSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo("NONE");
        assertThat(captor.getValue().getTempData()).isNull();
    }
}
