package behzoddev.testproject.telegram.service;

import behzoddev.testproject.telegram.dao.TelegramSessionRepository;
import behzoddev.testproject.telegram.entity.TelegramSession;
import behzoddev.testproject.telegram.state.BotState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Botdagi ko'p bosqichli suhbat holatini (TelegramSession) o'qish/yozish —
// TelegramBot/menu servislari bevosita entity/repository bilan ishlamasin
// deb, shu yerda bitta joyga jamlangan.
@Service
@RequiredArgsConstructor
public class TelegramSessionService {

    private final TelegramSessionRepository telegramSessionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BotState getState(Long chatId) {
        return telegramSessionRepository.findById(chatId)
                .map(s -> BotState.valueOf(s.getState()))
                .orElse(BotState.NONE);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Map<String, String> getTempData(Long chatId) {
        return telegramSessionRepository.findById(chatId)
                .map(TelegramSession::getTempData)
                .filter(json -> json != null && !json.isBlank())
                .map(json -> (Map<String, String>) objectMapper.readValue(json, Map.class))
                .orElseGet(HashMap::new);
    }

    // Holatni o'zgartiradi, temp_data'ga tegmaydi (oqim davomida bosqichdan
    // bosqichga o'tishda avvalgi kiritilgan qiymatlar saqlanib qolishi uchun).
    @Transactional
    public void setState(Long chatId, BotState state) {
        TelegramSession session = getOrCreate(chatId);
        session.setState(state.name());
        session.setUpdatedAt(LocalDateTime.now());
        telegramSessionRepository.save(session);
    }

    // Bitta maydonni temp_data'ga qo'shadi (mavjudlarini saqlab qolgan holda).
    @Transactional
    public void putTempData(Long chatId, String key, String value) {
        Map<String, String> data = getTempData(chatId);
        data.put(key, value);

        TelegramSession session = getOrCreate(chatId);
        session.setTempData(objectMapper.writeValueAsString(data));
        session.setUpdatedAt(LocalDateTime.now());
        telegramSessionRepository.save(session);
    }

    // Oqim yakunlanganda (muvaffaqiyatli yoki bekor qilinganda) — holatni
    // NONE'ga qaytaradi, temp_data'ni tozalaydi.
    @Transactional
    public void clear(Long chatId) {
        TelegramSession session = getOrCreate(chatId);
        session.setState(BotState.NONE.name());
        session.setTempData(null);
        session.setUpdatedAt(LocalDateTime.now());
        telegramSessionRepository.save(session);
    }

    private TelegramSession getOrCreate(Long chatId) {
        return telegramSessionRepository.findById(chatId)
                .orElseGet(() -> {
                    TelegramSession s = new TelegramSession();
                    s.setChatId(chatId);
                    s.setState(BotState.NONE.name());
                    s.setUpdatedAt(LocalDateTime.now());
                    return s;
                });
    }
}
