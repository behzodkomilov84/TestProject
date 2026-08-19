package behzoddev.testproject.telegram.service;

import behzoddev.testproject.telegram.TelegramBot;
import behzoddev.testproject.telegram.dao.TelegramSessionRepository;
import behzoddev.testproject.telegram.entity.TelegramSession;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Saytda Exam/Hard rejimida vaqt tugaganda test avtomatik yakunlanadi
 * (jonli sekundomer). Botda hech kim tugma bosmasa ham xuddi shunday
 * bo'lishi uchun — har 30 soniyada aktiv testlarni tekshirib, vaqti
 * tugaganlarini avtomatik yakunlab, foydalanuvchiga xabar yuboradi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramPracticeTestTimeoutServiceTest {

    @Mock
    private TelegramSessionRepository telegramSessionRepository;
    @Mock
    private TelegramPracticeTestService practiceTestService;
    @Mock
    private TelegramBot telegramBot;

    @Test
    void autoFinishExpiredTests_expiredSession_sendsMessage() throws Exception {
        TelegramPracticeTestTimeoutService service = new TelegramPracticeTestTimeoutService(
                telegramSessionRepository, practiceTestService, telegramBot);

        TelegramSession session = new TelegramSession();
        session.setChatId(111L);
        session.setState(BotState.IN_PRACTICE_TEST.name());

        when(telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name()))
                .thenReturn(List.of(session));

        SendMessage expiredMsg = new SendMessage();
        expiredMsg.setChatId("111");
        expiredMsg.setText("⏰ Vaqt tugadi — test avtomatik yakunlandi.");
        when(practiceTestService.autoFinishIfExpired(111L)).thenReturn(expiredMsg);

        service.autoFinishExpiredTests();

        verify(telegramBot).execute(expiredMsg);
    }

    @Test
    void autoFinishExpiredTests_notExpired_doesNotSendMessage() throws Exception {
        TelegramPracticeTestTimeoutService service = new TelegramPracticeTestTimeoutService(
                telegramSessionRepository, practiceTestService, telegramBot);

        TelegramSession session = new TelegramSession();
        session.setChatId(222L);
        session.setState(BotState.IN_PRACTICE_TEST.name());

        when(telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name()))
                .thenReturn(List.of(session));
        when(practiceTestService.autoFinishIfExpired(222L)).thenReturn(null);

        service.autoFinishExpiredTests();

        verify(telegramBot, never()).execute(any(SendMessage.class));
    }

    @Test
    void autoFinishExpiredTests_oneFailingSend_doesNotStopOthers() throws Exception {
        TelegramPracticeTestTimeoutService service = new TelegramPracticeTestTimeoutService(
                telegramSessionRepository, practiceTestService, telegramBot);

        TelegramSession first = new TelegramSession();
        first.setChatId(111L);
        first.setState(BotState.IN_PRACTICE_TEST.name());
        TelegramSession second = new TelegramSession();
        second.setChatId(222L);
        second.setState(BotState.IN_PRACTICE_TEST.name());

        when(telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name()))
                .thenReturn(List.of(first, second));

        SendMessage msg1 = new SendMessage();
        msg1.setChatId("111");
        SendMessage msg2 = new SendMessage();
        msg2.setChatId("222");

        when(practiceTestService.autoFinishIfExpired(111L)).thenReturn(msg1);
        when(practiceTestService.autoFinishIfExpired(222L)).thenReturn(msg2);
        when(telegramBot.execute(msg1)).thenThrow(new RuntimeException("Bloklangan"));

        service.autoFinishExpiredTests();

        verify(telegramBot).execute(eq(msg2)); // birinchisi xato bergan bo'lsa ham, ikkinchisi yuborilishi kerak
    }
}
