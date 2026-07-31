package behzoddev.testproject.service;

import behzoddev.testproject.dao.AssignmentChatRepository;
import behzoddev.testproject.dao.AssignmentRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.teacher.ChatMessageDto;
import behzoddev.testproject.entity.Assignment;
import behzoddev.testproject.entity.AssignmentChat;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AssignmentChatRepository assignmentChatRepository;

    @Transactional
    public void extendDue(Long id, String dueDateStr) {

        Assignment a = assignmentRepository.findById(id).orElseThrow();
        a.setDueDate(LocalDateTime.parse(dueDateStr.replace(" ", "T")));
    }

    @Transactional
    public void reassign(Long id) {

        Assignment old = assignmentRepository.findById(id).orElseThrow();

        Assignment copy = Assignment.builder()
                .group(old.getGroup())
                .questionSet(old.getQuestionSet())
                .assignedBy(old.getAssignedBy())
                .dueDate(old.getDueDate())
                .build();

        assignmentRepository.save(copy);
    }

    @Transactional
    public void bulkReassign(List<Long> ids) {
        ids.forEach(this::reassign);
    }

    @Transactional
    public void bulkExtend(List<Long> ids, String dueDate) {

        LocalDateTime d = LocalDateTime.parse(dueDate.replace(" ", "T"));

        assignmentRepository.findAllById(ids)
                .forEach(a -> a.setDueDate(d));
    }

    @Transactional
    public void sendMessage(Long assignmentId, Long senderId, String text) {

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        AssignmentChat chat = AssignmentChat.builder()
                .assignment(assignment)
                .sender(sender)
                .messageText(text)
                .build();

        assignmentChatRepository.save(chat);
    }


    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChat(Long assignmentId) {

        // 1. Проверяем что assignment существует
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        // 2. Получаем сообщения (с sender через EntityGraph)
        List<AssignmentChat> messages =
                assignmentChatRepository
                        .findByAssignmentIdAndDeletedFalseOrderByCreatedAtAsc(assignment.getId());

        // 3. Маппинг в DTO
        return messages.stream()
                .map(chat -> new ChatMessageDto(
                        chat.getId(),
                        chat.getSender().getId(),
                        chat.getSender().getUsername(),
                        chat.getMessageText(),
                        displayRole(chat.getSender()), // dual-role: eng "yuqori" rol ko'rsatiladi
                        chat.getCreatedAt()
                ))
                .toList();
    }

    // Dual-role tufayli foydalanuvchida bir nechta rol bo'lishi mumkin.
    // Chat'da bittagina "belgi" ko'rsatish uchun eng yuqori rolni tanlaymiz:
    // OWNER > ADMIN (o'qituvchi) > USER (o'quvchi).
    private String displayRole(User user) {
        if (user.hasRole("ROLE_OWNER")) return "ROLE_OWNER";
        if (user.hasRole("ROLE_ADMIN")) return "ROLE_ADMIN";
        return "ROLE_USER";
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatForUser(Long assignmentId, User user) {

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow();

        // Dual-role tufayli o'qituvchida ham ROLE_USER bo'lishi mumkin, shuning
        // uchun "ROLE_USER bormi" emas, balki "ADMIN/OWNER YO'Qmi" tekshiriladi —
        // faqat sof o'quvchi (elevated rolsiz) o'z guruhiga tegishli ekanligi
        // bo'yicha cheklanadi.
        boolean isPrivileged = user.hasRole("ROLE_ADMIN") || user.hasRole("ROLE_OWNER");

        if (!isPrivileged) {

            boolean belongs =
                    assignment.getGroup().getPupils()
                            .stream()
                            .anyMatch(p -> p.getId().equals(user.getId()));

            if (!belongs) {
                throw new AccessDeniedException("Not your assignment");
            }
        }

        return getChat(assignmentId);
    }
}
