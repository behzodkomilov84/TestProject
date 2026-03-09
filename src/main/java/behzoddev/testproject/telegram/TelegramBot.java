package behzoddev.testproject.telegram;

import behzoddev.testproject.telegram.service.TelegramUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final String token;
    private final String username;
    private final TelegramUserService telegramUserService;

    public TelegramBot(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username,
            TelegramUserService telegramUserService
    ) {
        super(token);
        this.token = token;
        this.username = username;
        this.telegramUserService = telegramUserService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message msg = update.getMessage();
                String text = msg.getText();
                SendMessage response;

                if ("/start".equals(text)) {
                    response = telegramUserService.handleStart(msg);

                } else if ("📚 Mening topshiriqlarim".equals(text)) {
                    response = telegramUserService.sendMyAssignments(msg.getChatId());

                } else if ("📊 Natijalarim".equals(text)) {

                    response = telegramUserService.sendMyResults(msg.getChatId());

                } else {
                    response = telegramUserService.handleMessage(msg);
                }

                if (response != null) execute(response);
            }
        } catch (Exception e) {
            log.error("Telegram update error", e);
        }
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }
}