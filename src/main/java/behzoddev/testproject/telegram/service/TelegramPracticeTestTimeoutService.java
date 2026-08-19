package behzoddev.testproject.telegram.service;

import behzoddev.testproject.telegram.TelegramBot;
import behzoddev.testproject.telegram.dao.TelegramSessionRepository;
import behzoddev.testproject.telegram.entity.TelegramSession;
import behzoddev.testproject.telegram.state.BotState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

// Saytda Exam/Hard rejimida jonli sekundomer bor: har soniyada qolgan
// vaqtni ko'rsatadi va 0'ga yetganda testni AVTOMATIK yakunlaydi. Botda
// esa hech narsa "o'z-o'zidan" sodir bo'lmaydi (bot faqat kelgan
// update'larga javob beradi) — shu farqni yopish uchun bu servis:
// 1) vaqti tugagan testlarni avtomatik yakunlaydi va xabar yuboradi;
// 2) hali faol testlar uchun BITTA xabarni davriy tahrirlab (EditMessageText)
//    qolgan vaqtni yangilab boradi — chatni yangi xabarlar bilan
//    "to'ldirmasdan", jonli sekundomerga eng yaqin taqlid.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPracticeTestTimeoutService {

    private final TelegramSessionRepository telegramSessionRepository;
    private final TelegramPracticeTestService practiceTestService;
    private final TelegramBot telegramBot;

    // 10 soniya — saytdagi har-soniyalik sekundomerdan farqli o'laroq,
    // Telegram'da har soniyada xabar tahrirlash o'rinsiz spam va API
    // cheklovlariga yaqinlashish bo'lardi; 10 soniya "deyarli real vaqt"
    // hissini beradi, vaqt tugashini aniqlashda kechikish ham qisqa bo'ladi.
    @Scheduled(fixedDelay = 10000)
    public void checkActiveTests() {
        List<TelegramSession> activeSessions =
                telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name());

        for (TelegramSession session : activeSessions) {
            Long chatId = session.getChatId();

            SendMessage expiredMsg = practiceTestService.autoFinishIfExpired(chatId);
            if (expiredMsg != null) {
                trySend(chatId, expiredMsg);
                continue;
            }

            tick(chatId);
        }
    }

    private void tick(Long chatId) {
        TelegramPracticeTestService.TickInfo info = practiceTestService.getTickInfo(chatId);
        if (info == null) return; // Practice rejimi (chegarasiz) yoki test hali boshlanmagan

        String text = "⏱ Qolgan vaqt: " + TelegramPracticeTestService.formatRemaining(info.deadlineEpochMilli());

        if (info.timerMessageId() == null) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(text);
            try {
                Message sent = telegramBot.execute(msg);
                practiceTestService.recordTimerMessageId(chatId, sent.getMessageId());
            } catch (Exception e) {
                log.error("Vaqt sanog'i xabarini yuborishda xatolik: chatId={}", chatId, e);
            }
            return;
        }

        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(info.timerMessageId())
                .text(text)
                .build();
        try {
            telegramBot.execute(edit);
        } catch (Exception e) {
            // Masalan, matn oldingisi bilan bir xil chiqib qolsa — Telegram
            // "message is not modified" xatosini qaytaradi, bu jiddiy emas.
            log.debug("Vaqt sanog'i xabarini yangilashda xatolik (odatda ahamiyatsiz): chatId={}", chatId, e);
        }
    }

    private void trySend(Long chatId, SendMessage msg) {
        try {
            telegramBot.execute(msg);
        } catch (Exception e) {
            log.error("Vaqti tugagan testni avtomatik yakunlash haqida xabar yuborishda xatolik: chatId={}",
                    chatId, e);
        }
    }
}
