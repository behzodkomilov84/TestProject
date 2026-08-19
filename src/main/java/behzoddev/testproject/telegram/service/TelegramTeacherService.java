package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.GroupInviteRepository;
import behzoddev.testproject.dao.QuestionSetRepository;
import behzoddev.testproject.dao.TeacherGroupRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.teacher.AssignDto;
import behzoddev.testproject.dto.teacher.AssignmentAdminRowDto;
import behzoddev.testproject.dto.teacher.AssignmentStudentDetailDto;
import behzoddev.testproject.dto.teacher.GroupStudentRowDto;
import behzoddev.testproject.dto.teacher.QuestionSetDto;
import behzoddev.testproject.dto.teacher.ResponseForGetTeacherGroupDto;
import behzoddev.testproject.entity.TeacherGroup;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.TeacherService;
import behzoddev.testproject.telegram.state.BotState;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Botda ADMIN (o'qituvchi) uchun: 👥 Gruppalarim, 📝 Topshiriq berish,
// 📈 O'quvchilar natijalari. Haqiqiy TeacherService orqali — saytdagi
// /teacher sahifasi bilan bir xil biznes-logika (ruxsat tekshiruvlari,
// bitta guruhga bitta topshiriq cheklovi va h.k.).
@Service
@RequiredArgsConstructor
public class TelegramTeacherService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Map<Integer, String> DUE_LABELS = Map.of(1, "1 kun", 3, "3 kun", 7, "1 hafta", 14, "2 hafta");

    private final TeacherService teacherService;
    private final TeacherGroupRepository teacherGroupRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final QuestionSetRepository questionSetRepository;
    private final UserRepository userRepository;
    private final TelegramSessionService sessionService;
    private final TelegramAutoLoginService autoLoginService;

    // ================= Gruppalar =================

    public SendMessage listGroups(User teacher) {
        List<ResponseForGetTeacherGroupDto> groups = teacherService.getTeacherGroupsByUser(teacher);

        SendMessage msg = new SendMessage();
        msg.setChatId(teacher.getTelegramId().toString());
        msg.setText(groups.isEmpty()
                ? "👥 Sizda hali guruh yo'q. Yangi guruh yarating:"
                : "👥 <b>Gruppalarim</b>\n\nBatafsil ko'rish uchun guruhni tanlang:");
        msg.setParseMode("HTML");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ResponseForGetTeacherGroupDto g : groups) {
            rows.add(List.of(button(g.groupName(), "tg_group_" + g.teacherGroupId())));
        }
        rows.add(List.of(button("➕ Yangi guruh yaratish", "tg_newgroup")));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage viewGroup(Long chatId, Long groupId) {
        TeacherGroup group = teacherGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Guruh topilmadi"));

        List<GroupStudentRowDto> students = teacherService.getGroupStudents(groupId);

        StringBuilder sb = new StringBuilder("👥 <b>" + escape(group.getName()) + "</b>\n\n");
        if (students.isEmpty()) {
            sb.append("Hozircha o'quvchi yo'q.");
        } else {
            for (GroupStudentRowDto s : students) {
                sb.append(statusEmoji(s.status())).append(" ").append(escape(s.username())).append("\n");
            }
        }

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());
        msg.setParseMode("HTML");

        InlineKeyboardButton inviteBtn = button("➕ O'quvchi taklif qilish", "tg_invite_" + groupId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(inviteBtn)));
        msg.setReplyMarkup(markup);
        return msg;
    }

    private String statusEmoji(String status) {
        return switch (status) {
            case "ACCEPTED" -> "✅";
            case "PENDING" -> "⏳";
            case "REJECTED" -> "❌";
            default -> "•";
        };
    }

    public SendMessage startCreateGroup(Long chatId) {
        sessionService.setState(chatId, BotState.AWAITING_GROUP_NAME);
        return prompt(chatId, "✏️ Yangi guruh nomini yozing:");
    }

    public SendMessage applyGroupName(Long chatId, String text) {
        User teacher = getUserByChatId(chatId);
        try {
            teacherService.createGroup(teacher, text.trim());
            sessionService.clear(chatId);
            return success(chatId, "✅ Guruh yaratildi: " + escape(text.trim()));
        } catch (AccessDeniedException e) {
            sessionService.clear(chatId);
            return success(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            return retry(chatId, "❌ Guruh nomi noto'g'ri.");
        }
    }

    public SendMessage startInvite(Long chatId, Long groupId) {
        sessionService.setState(chatId, BotState.AWAITING_INVITE_USERNAME);
        sessionService.putTempData(chatId, "tg_inviteGroupId", groupId.toString());
        return prompt(chatId, "✏️ Taklif qilmoqchi bo'lgan o'quvchining foydalanuvchi nomini (username) yozing:");
    }

    public SendMessage applyInviteUsername(Long chatId, String text) {
        Long groupId = Long.valueOf(sessionService.getTempData(chatId).get("tg_inviteGroupId"));

        User pupil = userRepository.findByUsername(text.trim()).orElse(null);
        if (pupil == null) {
            return retry(chatId, "❌ Bunday foydalanuvchi topilmadi: " + escape(text.trim()));
        }
        if (!pupil.hasRole("ROLE_USER")) {
            return retry(chatId, "❌ Bu foydalanuvchi o'quvchi (USER) emas.");
        }

        try {
            teacherService.inviteStudent(groupId, pupil.getId());
            sessionService.clear(chatId);
            return success(chatId, "✅ Taklif yuborildi: " + escape(pupil.getUsername()));
        } catch (Exception e) {
            sessionService.clear(chatId);
            return success(chatId, "❌ Taklif yuborishda xatolik: " + e.getMessage());
        }
    }

    // ================= Topshiriq berish =================

    public SendMessage startAssignFlow(User teacher) {
        List<ResponseForGetTeacherGroupDto> groups = teacherService.getTeacherGroupsByUser(teacher);

        SendMessage msg = new SendMessage();
        msg.setChatId(teacher.getTelegramId().toString());

        if (groups.isEmpty()) {
            msg.setText("📝 Avval guruh yarating (👥 Gruppalarim bo'limidan).");
            return msg;
        }

        msg.setText("📝 Topshiriq berish uchun guruhni tanlang:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ResponseForGetTeacherGroupDto g : groups) {
            rows.add(List.of(button(g.groupName(), "tg_assign_group_" + g.teacherGroupId())));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage selectAssignGroup(Long chatId, Long groupId) {
        User teacher = getUserByChatId(chatId);
        List<QuestionSetDto> sets = teacherService.getSets(teacher);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (sets.isEmpty()) {
            String url = autoLoginService.buildLoginUrl(teacher, "/teacher");
            msg.setText("📝 Sizda hali savollar paketi (question set) yo'q. Avval saytda yarating: " + url);
            return msg;
        }

        sessionService.putTempData(chatId, "tg_assignGroupId", groupId.toString());
        msg.setText("📝 Savollar paketini tanlang:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (QuestionSetDto s : sets) {
            rows.add(List.of(button(s.name() + " (" + s.questionIds().size() + " ta savol)", "tg_assign_set_" + s.id())));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage selectAssignSet(Long chatId, Long setId) {
        sessionService.putTempData(chatId, "tg_assignSetId", setId.toString());

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("📝 Muddatni tanlang:");

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Map.Entry<Integer, String> e : DUE_LABELS.entrySet()) {
            row.add(button(e.getValue(), "tg_assign_due_" + e.getKey()));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage finalizeAssign(Long chatId, int days) {
        User teacher = getUserByChatId(chatId);
        Map<String, String> temp = sessionService.getTempData(chatId);
        Long groupId = Long.valueOf(temp.get("tg_assignGroupId"));
        Long setId = Long.valueOf(temp.get("tg_assignSetId"));
        LocalDateTime dueDate = LocalDateTime.now().plusDays(days);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        try {
            teacherService.assignQuestionSetToStudents(teacher, new AssignDto(groupId, setId, dueDate, null));
            sessionService.clear(chatId);
            msg.setText("✅ Topshiriq berildi! Muddat: " + dueDate.format(DATE_FORMAT));
        } catch (Exception e) {
            sessionService.clear(chatId);
            msg.setText("❌ " + e.getMessage());
        }

        return msg;
    }

    // ================= Natijalar =================

    public SendMessage listResults(User teacher) {
        List<AssignmentAdminRowDto> assignments = teacherService.getAllAssignments(teacher);

        SendMessage msg = new SendMessage();
        msg.setChatId(teacher.getTelegramId().toString());

        if (assignments.isEmpty()) {
            msg.setText("📈 Hozircha topshiriqlar yo'q.");
            return msg;
        }

        msg.setText("📈 Batafsil ko'rish uchun topshiriqni tanlang:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (AssignmentAdminRowDto a : assignments) {
            String label = a.questionSetName() + " — " + a.groupName() +
                    " (" + a.finished() + "/" + a.totalStudents() + ")";
            rows.add(List.of(button(label, "tg_result_" + a.id())));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    public SendMessage showResultDetail(Long chatId, Long assignmentId) {
        List<AssignmentStudentDetailDto> details = teacherService.getAssignmentDetails(assignmentId);

        StringBuilder sb = new StringBuilder("📈 <b>Natijalar</b>\n\n");
        for (AssignmentStudentDetailDto d : details) {
            sb.append(statusEmojiForAssignment(d.status())).append(" ").append(escape(d.pupilName()));
            if (d.percent() != null && !"NEW".equals(d.status())) {
                sb.append(" — ").append(d.percent()).append("%");
            }
            sb.append("\n");
        }

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());
        msg.setParseMode("HTML");
        return msg;
    }

    private String statusEmojiForAssignment(String status) {
        return switch (status) {
            case "FINISHED" -> "✅";
            case "IN_PROGRESS" -> "⏳";
            default -> "🆕";
        };
    }

    // ================= Yordamchi metodlar =================

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private SendMessage prompt(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text + "\n\n(Bekor qilish uchun /cancel yozing)");
        return msg;
    }

    private SendMessage retry(Long chatId, String errorText) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(errorText + "\n\nQayta urinib ko'ring, yoki /cancel yozing.");
        return msg;
    }

    private SendMessage success(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        return msg;
    }

    public SendMessage cancelFlow(Long chatId) {
        sessionService.clear(chatId);
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("❎ Bekor qilindi.");
        return msg;
    }

    private User getUserByChatId(Long chatId) {
        return userRepository.findByTelegramId(chatId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
