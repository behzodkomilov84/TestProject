package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.AttemptAnswerRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.student.AttemptFullDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TelegramQuizService {
    private final AssignmentAttemptService assignmentAttemptService;
    private final UserRepository userRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;

    public SendMessage sendQuestion(Long chatId, Long attemptId, int index) {

        List<Question> questions =
                assignmentAttemptService.getQuestionsForAttempt(attemptId);

        for (Question question : questions) {
            Collections.shuffle(question.getAnswers());
        }

        if (index >= questions.size()) {

            User pupil = getUserByChatId(chatId);

            assignmentAttemptService.finishTaskSession(pupil, attemptId);

            return sendFinishMessage(chatId, attemptId);
        }

        Question q = questions.get(index);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        msg.setText(buildQuestionText(q, index, questions.size()));

        msg.setReplyMarkup(
                buildAnswerButtons(q, attemptId, index)
        );

        return msg;
    }

    public EditMessageText editQuestion(Long chatId, Integer messageId, Long attemptId, int index) {

        List<Question> questions =
                assignmentAttemptService.getQuestionsForAttempt(attemptId);

        for (Question question : questions) {
            Collections.shuffle(question.getAnswers());
        }

        if (index >= questions.size()) {

            User pupil = getUserByChatId(chatId);

            assignmentAttemptService.finishTaskSession(pupil, attemptId);

            return sendFinishMessageForEditMessageText(chatId, attemptId);
        }

        Question q = questions.get(index);

        EditMessageText edit = new EditMessageText();

        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);

        edit.setText(buildQuestionText(q, index, questions.size()));

        edit.setReplyMarkup(
                buildAnswerButtons(q, attemptId, index)
        );

        return edit;
    }

    @NotNull
    private String buildQuestionText(@NotNull Question q, int index, int total) {

        String progress = buildProgressBar(index, total);

        StringBuilder text = new StringBuilder();

        text.append("❓ Savol ")
                .append(index + 1)
                .append("/")
                .append(total)
                .append("\n");

        text.append("📊 Progress\n")
                .append(progress)
                .append("\n\n");

        text.append("❓ ")
                .append(q.getQuestionText())
                .append("\n\n");

        char option = 'A';

        for (Answer a : q.getAnswers()) {

            text.append(option)
                    .append(") ")
                    .append(a.getAnswerText())
                    .append("\n\n");

            option++;
        }

        return text.toString();
    }

    @NotNull
    private InlineKeyboardMarkup buildAnswerButtons(@NotNull Question q, Long attemptId, int index) {

        List<InlineKeyboardButton> row = new ArrayList<>();

        char option = 'A';

        for (Answer a : q.getAnswers()) {

            InlineKeyboardButton btn = new InlineKeyboardButton();

            btn.setText(String.valueOf(option));

            btn.setCallbackData(
                    "answer_" +
                            attemptId + "_" +
                            q.getId() + "_" +
                            a.getId() + "_" +
                            index
            );

            row.add(btn);

            option++;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row));

        return markup;
    }

    private User getUserByChatId(Long chatId) {
        return userRepository
                .findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }

    public SendMessage sendFinishMessage(@NotNull Long chatId, Long attemptId) {

        AttemptFullDto attempt =
                assignmentAttemptService.getFullAttemptForTestSessionOfBot(
                        attemptId
                );

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        msg.setText(
                "⭐ Natija: " + attempt.percent() + "%\n"
                        + "✔ To'g'ri: "
                        + attempt.correctAnswers()
                        + "/"
                        + attempt.totalQuestions()
        );

        return msg;
    }

    public EditMessageText sendFinishMessageForEditMessageText(@NotNull Long chatId, Long attemptId) {

        AttemptFullDto attempt =
                assignmentAttemptService.getFullAttemptForTestSessionOfBot(
                        attemptId
                );

        EditMessageText msg = new EditMessageText();
        msg.setChatId(chatId.toString());

        msg.setText(
                "⭐ Natija: " + attempt.percent() + "%\n"
                        + "✔ To'g'ri: "
                        + attempt.correctAnswers()
                        + "/"
                        + attempt.totalQuestions()
        );

        return msg;
    }

    public int getCurrentQuestionIndex(Long attemptId) {

        long answered =
                attemptAnswerRepository.countByAssignmentAttempt_Id(attemptId);

        return (int) answered;
    }

    public SendMessage resumeAttempt(Long chatId, Long attemptId) {

        int index = getCurrentQuestionIndex(attemptId);

        return sendQuestion(chatId, attemptId, index);
    }

    private String buildProgressBar(int current, int total) {

        int size = 12; // длина полоски

        double percent = (double) current / total;

        int filled = (int) Math.round(size * percent);

        String bar =
                "█".repeat(filled) +
                        "░".repeat(size - filled);

        int percentValue = (int) (percent * 100);

        return bar + " " + percentValue + "%";
    }
}
