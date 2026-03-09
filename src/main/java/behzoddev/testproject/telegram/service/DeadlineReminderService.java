package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentRecipient;
import behzoddev.testproject.telegram.TelegramBot;
import lombok.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeadlineReminderService {

    private final AssignmentRepository assignmentRepository;
    private final TelegramBot telegramBot;

    @SneakyThrows
    @Scheduled(cron = "0 0 9 * * *") // каждый день в 09:00
    public void sendDeadlineReminders() {

        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);

        List<Assignment> assignments =
                assignmentRepository.findAssignmentsDueSoon(tomorrow);

        for (Assignment a : assignments) {

            for (AssignmentRecipient r : a.getRecipients()) {

                Long telegramId = r.getPupil().getTelegramId();

                if (telegramId == null)
                    continue;

                SendMessage msg = new SendMessage();

                msg.setChatId(telegramId.toString());

                msg.setText(
                        "⏰ Eslatma!\n\n" +
                                "Topshiriq: " + a.getQuestionSet().getName() +
                                "\nTopshiriq beruvchi: " + a.getAssignedBy().getUsername() +
                                "\nSavollar soni: " + a.getQuestionSet().getQuestions().size() + " ta" +
                                "\nMuddat: " + a.getDueDate()
                );

                telegramBot.execute(msg);
            }
        }
    }
}
