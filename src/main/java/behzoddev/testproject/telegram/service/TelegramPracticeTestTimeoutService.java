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

import java.util.List;

// Saytda Exam/Hard rejimida vaqt tugaganda test avtomatik yakunlanadi
// (testSession.js'dagi jonli sekundomer 0'ga yetganda finishTest()ni
// o'zi chaqiradi) — botda esa foydalanuvchi hech qanday tugma bosmasa,
// hech narsa "o'z-o'zidan" sodir bo'lmaydi (bot faqat kelgan update'larga
// javob beradi). Shu farqni yopish uchun — har 30 soniyada barcha
// "IN_PRACTICE_TEST" holatidagi suhbatlarni tekshirib, vaqti tugaganlarini
// avtomatik yakunlaydi va foydalanuvchiga xabar yuboradi.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPracticeTestTimeoutService {

    private final TelegramSessionRepository telegramSessionRepository;
    private final TelegramPracticeTestService practiceTestService;
    private final TelegramBot telegramBot;

    @Scheduled(fixedDelay = 30000)
    public void autoFinishExpiredTests() {
        List<TelegramSession> activeSessions =
                telegramSessionRepository.findByState(BotState.IN_PRACTICE_TEST.name());

        for (TelegramSession session : activeSessions) {
            SendMessage msg = practiceTestService.autoFinishIfExpired(session.getChatId());
            if (msg == null) continue; // vaqti hali tugamagan (yoki Practice rejimi — chegara yo'q)

            try {
                telegramBot.execute(msg);
            } catch (Exception e) {
                log.error("Vaqti tugagan testni avtomatik yakunlash haqida xabar yuborishda xatolik: chatId={}",
                        session.getChatId(), e);
            }
        }
    }
}
