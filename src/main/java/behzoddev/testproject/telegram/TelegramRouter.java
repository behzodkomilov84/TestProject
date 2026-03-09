package behzoddev.testproject.telegram;

import behzoddev.testproject.telegram.callback.CallbackDataParser;
import behzoddev.testproject.telegram.service.TelegramAssignmentService;
import behzoddev.testproject.telegram.service.TelegramAttemptService;
import behzoddev.testproject.telegram.service.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class TelegramRouter {

    private final TelegramAssignmentService assignmentService;
    private final TelegramAttemptService attemptService;
    private final TelegramUserService userService;
    private final CallbackDataParser parser;

    public SendMessage route(Update update) {

        if (update.hasMessage())
            return handleMessage(update);

        if (update.hasCallbackQuery())
            return handleCallback(update);

        return null;
    }

    private SendMessage handleMessage(Update update) {

        var message = update.getMessage();

        if (message.getText() == null)
            return null;

        String text = message.getText();

        if ("/start".equals(text))
            return userService.handleStart(message);

        if ("/tasks".equals(text))
            return assignmentService.showAssignments(message);

        return null;
    }

    private SendMessage handleCallback(Update update) {

        var callback = update.getCallbackQuery();

        var parsed = parser.parse(callback.getData());

        return switch (parsed.type()) {
            case "assignment" -> assignmentService.openAssignment(callback, parsed.assignmentId());
            case "answer" -> attemptService.processAnswer(
                    callback,
                    parsed.assignmentId(),
                    parsed.answerId()
            );
            default -> null;
        };

    }
}