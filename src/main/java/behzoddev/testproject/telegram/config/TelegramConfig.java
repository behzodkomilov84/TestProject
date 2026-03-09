package behzoddev.testproject.telegram.config;

import behzoddev.testproject.telegram.TelegramBot;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramConfig {

    @SneakyThrows
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBot telegramBot){

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(telegramBot);

        return api;
    }
}
