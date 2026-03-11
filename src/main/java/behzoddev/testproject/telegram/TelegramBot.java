package behzoddev.testproject.telegram;

import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.student.AnswerSyncDto;
import behzoddev.testproject.dto.student.AttemptDto;
import behzoddev.testproject.dto.student.SyncAttemptRequestDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import behzoddev.testproject.telegram.service.TelegramQuizService;
import behzoddev.testproject.telegram.service.TelegramUserService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final String token;
    private final String username;
    private final TelegramUserService telegramUserService;
    private final UserRepository userRepository;
    private final AssignmentAttemptService assignmentAttemptService;
    private final AssignmentRepository assignmentRepository;
    private final TelegramQuizService telegramQuizService;

    public TelegramBot(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username,
            TelegramUserService telegramUserService,
            UserRepository userRepository,
            AssignmentAttemptService assignmentAttemptService,
            AssignmentRepository assignmentRepository,
            TelegramQuizService telegramQuizService) {
        super(token);
        this.token = token;
        this.username = username;
        this.telegramUserService = telegramUserService;
        this.userRepository = userRepository;
        this.assignmentAttemptService = assignmentAttemptService;
        this.assignmentRepository = assignmentRepository;
        this.telegramQuizService = telegramQuizService;
    }

    @Override
    public void onUpdateReceived(Update update) {

        try {

            if (update.hasCallbackQuery()) {

                String data = update.getCallbackQuery().getData();
                Long chatId = update.getCallbackQuery().getMessage().getChatId();

                if (data.startsWith("assignment_")) {

                    Long assignmentId =
                            Long.parseLong(data.replace("assignment_", ""));

                    SendMessage msg =
                            telegramUserService.showAssignmentInfo(chatId, assignmentId);

                    execute(msg);
                }

                if (data.startsWith("start_test_")) {

                    Long assignmentId =
                            Long.parseLong(data.replace("start_test_", ""));

                    User pupil = getUserByChatId(chatId);

                    AttemptDto attempt =
                            assignmentAttemptService.startAttempt(assignmentId, pupil);

                    SendMessage msg = getSendMessage(chatId, attempt);

                    execute(msg);
                }

                if (data.startsWith("answer_")) {

                    String[] parts = data.split("_");

                    Long attemptId = Long.parseLong(parts[1]);
                    Long questionId = Long.parseLong(parts[2]);
                    Long answerId = Long.parseLong(parts[3]);
                    int index = Integer.parseInt(parts[4]);

                    SyncAttemptRequestDto dto =
                            new SyncAttemptRequestDto(
                                    attemptId,
                                    List.of(new AnswerSyncDto(questionId, answerId))
                            );

                    User pupil = getUserByChatId(chatId);

                    assignmentAttemptService.syncAttempt(pupil, dto);

                    SendMessage msg =
                            telegramQuizService.sendQuestion(
                                    chatId,
                                    attemptId,
                                    index + 1
                            );

                    execute(msg);
                }
                return;
            }

            if (update.hasMessage() && update.getMessage().hasText()) {
                Message msg = update.getMessage();
                String text = msg.getText();
                SendMessage response;

                if (text.equals("/start")) {
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

    private User getUserByChatId(Long chatId) {
        return userRepository
                .findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    private @NotNull SendMessage getSendMessage(Long chatId, AttemptDto attempt) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (attempt.finishedAt() != null) {

            msg.setText("Bu test allaqachon yakunlangan. \nYakunlangan vaqt: "
                    + attempt.finishedAt().format(TelegramUserService.DATE_TIME_FORMATTER));
        } else {
            msg = telegramQuizService.sendQuestion(chatId, attempt.attemptId(), 0);
        }
        return msg;
    }
}