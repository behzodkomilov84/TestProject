package behzoddev.testproject.telegram.service;

import behzoddev.testproject.telegram.TelegramBot;
import behzoddev.testproject.telegram.dao.TelegramSessionRepository;
import behzoddev.testproject.telegram.entity.TelegramSession;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Saytda Exam/Hard rejimida jonli sekundomer bor — har soniyada qolgan
 * vaqtni ko'rsatadi va 0'ga yetganda testni avtomatik yakunlaydi. Botda
 * hech kim tugma bosmasa ham xuddi shunday bo'lishi uchun — bu servis
 * har 10 soniyada aktiv testlarni tekshiradi: vaqti tugaganlarini
 * avtomatik yakunlaydi, hali faollarining esa qolgan vaqt xabarini
 * (bitta xabarni tahrirlab) yangilab boradi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramPracticeTestTimeoutServiceTest {

    @Mock
    private TelegramSessionRepository telegramSessionRepository;
    @Mock
    private TelegramPracticeTestService practiceTestService;
    @Mock
    private TelegramBot telegramBot;

    private TelegramPracticeTestTimeoutService newService() {
        return new TelegramPracticeTestTimeoutService(telegramSessionRepository, practiceTestService, telegramBot);
    }

    // ===== Vaqti tugagan testlarni avtomatik yakunlash =====

    @Test
    void checkActiveTests_expiredSession_sendsMessage() throws Exception {
        TelegramPracticeTestTimeoutService service = newService();

        TelegramSession session = new TelegramSession();
        session.setChatId(111L);
        session.setState(BotState.IN_PRACTICE_TEST.name());

        when(telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name()))
                .thenReturn(List.of(session));

        SendMessage expiredMsg = new SendMessage();
        expiredMsg.setChatId("111");
        expiredMsg.setText("⏰ Vaqt tugadi — test avtomatik yakunlandi.");
        when(practiceTestService.autoFinishIfExpired(111L)).thenReturn(expiredMsg);

        service.checkActiveTests();

        verify(telegramBot).execute(expiredMsg);
    }

    @Test
    void checkActiveTests_oneFailingSend_doesNotStopOthers() throws Exception {
        TelegramPracticeTestTimeoutService service = newService();

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

        service.checkActiveTests();

        verify(telegramBot).execute(eq(msg2)); // birinchisi xato bergan bo'lsa ham, ikkinchisi yuborilishi kerak
    }

    // ===== Jonli qolgan vaqt sanog'i (tick) =====

    @Test
    void checkActiveTests_notExpiredPracticeMode_sendsNothing() throws Exception {
        TelegramPracticeTestTimeoutService service = newService();

        TelegramSession session = new TelegramSession();
        session.setChatId(333L);
        session.setState(BotState.IN_PRACTICE_TEST.name());

        when(telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name()))
                .thenReturn(List.of(session));
        when(practiceTestService.autoFinishIfExpired(333L)).thenReturn(null);
        when(practiceTestService.getTickInfo(333L)).thenReturn(null); // Practice — chegara yo'q

        service.checkActiveTests();

        verify(telegramBot, never()).execute(any(SendMessage.class));
        verify(telegramBot, never()).execute(any(EditMessageText.class));
    }

    @Test
    void checkActiveTests_firstTick_sendsNewMessageAndRecordsItsId() throws Exception {
        TelegramPracticeTestTimeoutService service = newService();

        TelegramSession session = new TelegramSession();
        session.setChatId(444L);
        session.setState(BotState.IN_PRACTICE_TEST.name());

        when(telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name()))
                .thenReturn(List.of(session));
        when(practiceTestService.autoFinishIfExpired(444L)).thenReturn(null);

        long deadline = System.currentTimeMillis() + 60_000;
        when(practiceTestService.getTickInfo(444L))
                .thenReturn(new TelegramPracticeTestService.TickInfo(deadline, null));

        Message sentMessage = new Message();
        sentMessage.setMessageId(9999);
        when(telegramBot.execute(any(SendMessage.class))).thenReturn(sentMessage);

        service.checkActiveTests();

        verify(telegramBot).execute(any(SendMessage.class));
        verify(practiceTestService).recordTimerMessageId(444L, 9999);
        verify(telegramBot, never()).execute(any(EditMessageText.class));
    }

    @Test
    void checkActiveTests_subsequentTick_editsExistingMessageInstead() throws Exception {
        TelegramPracticeTestTimeoutService service = newService();

        TelegramSession session = new TelegramSession();
        session.setChatId(555L);
        session.setState(BotState.IN_PRACTICE_TEST.name());

        when(telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name()))
                .thenReturn(List.of(session));
        when(practiceTestService.autoFinishIfExpired(555L)).thenReturn(null);

        long deadline = System.currentTimeMillis() + 60_000;
        when(practiceTestService.getTickInfo(555L))
                .thenReturn(new TelegramPracticeTestService.TickInfo(deadline, 777));

        service.checkActiveTests();

        verify(telegramBot, never()).execute(any(SendMessage.class));
        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramBot).execute(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo(777);
        assertThat(captor.getValue().getText()).contains("Qolgan vaqt");
        verify(practiceTestService, never()).recordTimerMessageId(any(), any());
    }
}
