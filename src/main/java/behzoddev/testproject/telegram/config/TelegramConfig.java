package behzoddev.testproject.telegram.config;

import behzoddev.testproject.telegram.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

// Bot ro'yxatdan o'tkazish (registerBot) ichida Telegram serverlariga
// (api.telegram.org) tarmoq so'rovi (eski webhook'ni tozalash) yuboriladi —
// agar shu vaqtda internet/Telegram vaqtincha ishlamasa, avval bu XATOLIK
// butun ilovani ishga tushirishni to'xtatib qo'yardi (bitta tashqi
// xizmatga ulanolmaslik butun platformani ag'darib yuborishi kerak emas).
// Endi: (1) xatolik bo'lsa ilova baribir ishga tushadi, (2) muvaffaqiyatli
// bo'lmaguncha har 30 soniyada fonda avtomatik qayta urinib turiladi —
// tarmoq tiklangach, ilovani qayta ishga tushirmasdan ham bot o'zi ishga tushadi.
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TelegramConfig {

    private final TelegramBot telegramBot;

    private TelegramBotsApi api;
    private volatile boolean registered = false;

    @Bean
    public TelegramBotsApi telegramBotsApi() {
        api = createApi();
        tryRegister();
        return api;
    }

    @Scheduled(fixedDelay = 30000)
    public void retryRegistrationIfNeeded() {
        if (!registered) {
            tryRegister();
        }
    }

    @SneakyThrows
    private TelegramBotsApi createApi() {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    private void tryRegister() {
        try {
            api.registerBot(telegramBot);
            registered = true;
            log.info("Telegram bot muvaffaqiyatli ro'yxatdan o'tkazildi.");
            setupCommandMenu();
        } catch (Exception e) {
            log.warn("Telegram botni ro'yxatdan o'tkazib bo'lmadi (internet/Telegram server vaqtincha " +
                    "ishlamayotgan bo'lishi mumkin) — 30 soniyadan keyin qayta urinib ko'riladi.", e);
        }
    }

    // "/" bosilganda Telegram mijozida chiqadigan native buyruqlar ro'yxati.
    // Muvaffaqiyatsiz bo'lsa ham bot ishlashda davom etadi — bu shunchaki
    // qulaylik, funksionallik uchun shart emas.
    private void setupCommandMenu() {
        try {
            List<BotCommand> commands = List.of(
                    BotCommand.builder().command("start").description("Botni ishga tushirish / asosiy menyu").build(),
                    BotCommand.builder().command("menu").description("Asosiy menyuni qayta ko'rsatish").build(),
                    BotCommand.builder().command("link").description("Akkauntni ulash (masalan: /link 123456)").build(),
                    BotCommand.builder().command("pay").description("To'lov so'rovi yuborish (masalan: /pay 50000)").build(),
                    BotCommand.builder().command("cancel").description("Joriy amalni bekor qilish").build()
            );

            telegramBot.execute(SetMyCommands.builder().commands(commands).build());
            log.info("Telegram bot buyruqlar menyusi o'rnatildi.");
        } catch (Exception e) {
            log.warn("Bot buyruqlar menyusini o'rnatib bo'lmadi (funksionallikka ta'sir qilmaydi).", e);
        }
    }
}
