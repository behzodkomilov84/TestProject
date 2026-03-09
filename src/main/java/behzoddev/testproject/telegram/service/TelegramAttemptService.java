package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TelegramAttemptService {

    private final AssignmentAttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;

    public SendMessage processAnswer(
            CallbackQuery callbackQuery,
            Long assignmentId,
            Long answerId
    ) {

        User user =
                userRepository
                        .findByTelegramId(callbackQuery.getFrom().getId())
                        .orElseThrow();

        AssignmentAttempt attempt =
                attemptRepository
                        .findByAssignmentIdAndPupilId(
                                assignmentId,
                                user.getId())
                        .orElseThrow();

        Answer answer =
                answerRepository.findById(answerId)
                        .orElseThrow();

        // здесь логика проверки ответа

        SendMessage msg = new SendMessage();
        msg.setChatId(callbackQuery.getMessage().getChatId().toString());
        msg.setText("Javob qabul qilindi");

        return msg;
    }


}