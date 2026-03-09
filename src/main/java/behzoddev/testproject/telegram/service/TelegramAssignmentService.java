package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class TelegramAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    public SendMessage showAssignments(Message message) {

        Long telegramId = message.getFrom().getId();

        User user = userRepository
                .findByTelegramId(telegramId)
                .orElseThrow();

        List<Assignment> assignments =
                assignmentRepository.findForStudent(user.getId());

        SendMessage msg = new SendMessage();
        msg.setChatId(message.getChatId().toString());
        msg.setText("Sizning topshiriqlaringiz:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Assignment a : assignments) {

            InlineKeyboardButton btn = new InlineKeyboardButton();

            btn.setText(a.getQuestionSet().getName());
            btn.setCallbackData("assignment_" + a.getId());

            rows.add(List.of(btn));
        }

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);

        return msg;
    }

    public SendMessage openAssignment(CallbackQuery cb, Long assignmentId) {

        SendMessage msg = new SendMessage();
        msg.setChatId(cb.getMessage().getChatId().toString());
        msg.setText("Topshiriq boshlandi");

        return msg;
    }
}