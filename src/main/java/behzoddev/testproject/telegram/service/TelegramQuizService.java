package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.student.AttemptFullDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TelegramQuizService {
    private final AssignmentAttemptService assignmentAttemptService;
    private final UserRepository userRepository;

    public SendMessage sendQuestion(Long chatId, Long attemptId, int index) {

        List<Question> questions =
                assignmentAttemptService.getQuestionsForAttempt(attemptId);

        if (index >= questions.size()) {

            User pupil = getUserByChatId(chatId);

            assignmentAttemptService.finishTaskSession(pupil, attemptId);

            return sendFinishMessage(chatId, attemptId);
        }

        Question q = questions.get(index);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        msg.setText(
                "❓ Savol " + (index + 1) + "/" + questions.size()
                        + "\n\n"
                        + q.getQuestionText()
        );

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Answer a : q.getAnswers()) {

            InlineKeyboardButton btn =
                    new InlineKeyboardButton();

            btn.setText(a.getAnswerText());

            btn.setCallbackData(
                    "answer_" +
                            attemptId + "_" +
                            q.getId() + "_" +
                            a.getId() + "_" +
                            index
            );

            rows.add(List.of(btn));
        }

        InlineKeyboardMarkup markup =
                new InlineKeyboardMarkup(rows);

        msg.setReplyMarkup(markup);

        return msg;
    }

    private User getUserByChatId(Long chatId) {
        return userRepository
                .findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }

    public SendMessage sendFinishMessage(Long chatId, Long attemptId) {

        AttemptFullDto attempt =
                assignmentAttemptService.getFullAttemptForTestSessionOfBot(
                        attemptId
                );

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        msg.setText(
                "🎉 Test yakunlandi!\n\n"
                        + "⭐ Natija: " + attempt.percent() + "%\n"
                        + "✔ To'g'ri: "
                        + attempt.correctAnswers()
                        + "/"
                        + attempt.totalQuestions()
        );

        return msg;
    }
}
