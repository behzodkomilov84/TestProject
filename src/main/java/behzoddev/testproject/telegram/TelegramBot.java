package behzoddev.testproject.telegram;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.student.AnswerSyncDto;
import behzoddev.testproject.dto.student.AttemptDto;
import behzoddev.testproject.dto.student.SyncAttemptRequestDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import behzoddev.testproject.service.NotificationService;
import behzoddev.testproject.telegram.service.TelegramMenuService;
import behzoddev.testproject.telegram.service.TelegramPracticeTestService;
import behzoddev.testproject.telegram.service.TelegramProfileService;
import behzoddev.testproject.telegram.service.TelegramQuizService;
import behzoddev.testproject.telegram.service.TelegramSessionService;
import behzoddev.testproject.telegram.service.TelegramUserService;
import behzoddev.testproject.telegram.state.BotState;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static behzoddev.testproject.telegram.service.TelegramMenuService.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final String token;
    private final String username;
    private final TelegramUserService telegramUserService;
    private final UserRepository userRepository;
    private final AssignmentAttemptService assignmentAttemptService;
    private final TelegramQuizService telegramQuizService;
    private final NotificationService notificationService;
    private final TelegramSessionService sessionService;
    private final TelegramMenuService menuService;
    private final TelegramProfileService profileService;
    private final TelegramPracticeTestService practiceTestService;

    public TelegramBot(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username,
            TelegramUserService telegramUserService,
            UserRepository userRepository,
            AssignmentAttemptService assignmentAttemptService,
            TelegramQuizService telegramQuizService,
            NotificationService notificationService,
            TelegramSessionService sessionService,
            TelegramMenuService menuService,
            TelegramProfileService profileService,
            TelegramPracticeTestService practiceTestService) {
        super(token);
        this.token = token;
        this.username = username;
        this.telegramUserService = telegramUserService;
        this.userRepository = userRepository;
        this.assignmentAttemptService = assignmentAttemptService;
        this.telegramQuizService = telegramQuizService;
        this.notificationService = notificationService;
        this.sessionService = sessionService;
        this.menuService = menuService;
        this.profileService = profileService;
        this.practiceTestService = practiceTestService;
    }

    @Override
    public void onUpdateReceived(Update update) {

        try {

            if (update.hasCallbackQuery()) {

                // убирает loading на кнопке в Telegram
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                execute(answer);

                String data = update.getCallbackQuery().getData();
                Long chatId = update.getCallbackQuery().getMessage().getChatId();

                if (data.equals("notif_read_all")) {
                    User user = getUserByChatId(chatId);
                    menuService.markAllNotificationsRead(user);
                    execute(menuService.showNotifications(user));
                    return;
                }

                if (data.startsWith("notif_read_")) {

                    Long notificationId = Long.parseLong(data.replace("notif_read_", ""));

                    User pupil = getUserByChatId(chatId);

                    try {
                        notificationService.markRead(notificationId, pupil);

                        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
                        String originalText = ((Message) update.getCallbackQuery().getMessage()).getText();

                        EditMessageText edit = new EditMessageText();
                        edit.setChatId(chatId.toString());
                        edit.setMessageId(messageId);
                        edit.setText(originalText + "\n\n✅ O'qilgan deb belgilandi");

                        execute(edit);
                    } catch (Exception e) {
                        log.warn("Bildirishnomani o'qilgan deb belgilashda xatolik", e);
                    }
                    return;
                }

                // ===== Profil tahrirlash (inline tugmalar) =====
                if (data.equals("profile_edit_username")) {
                    execute(profileService.startEditUsername(chatId));
                    return;
                }
                if (data.equals("profile_edit_email")) {
                    execute(profileService.startEditEmail(chatId));
                    return;
                }
                if (data.equals("profile_edit_phone")) {
                    execute(profileService.startEditPhone(chatId));
                    return;
                }
                if (data.equals("profile_edit_password")) {
                    execute(profileService.startEditPassword(chatId));
                    return;
                }

                // ===== Obuna: Click orqali 1 oyga to'lash =====
                if (data.equals("pay_click_1m")) {
                    execute(menuService.createClickPaymentLink(getUserByChatId(chatId)));
                    return;
                }

                // ===== Mustaqil test (fan -> savol soni -> savol-javob) =====
                if (data.startsWith("pt_science_")) {
                    Long scienceId = Long.parseLong(data.replace("pt_science_", ""));
                    execute(practiceTestService.selectScience(chatId, scienceId));
                    return;
                }
                if (data.startsWith("pt_count_")) {
                    int count = Integer.parseInt(data.replace("pt_count_", ""));
                    execute(practiceTestService.startTest(chatId, count));
                    return;
                }
                if (data.startsWith("pt_answer_")) {
                    Long answerId = Long.parseLong(data.replace("pt_answer_", ""));
                    execute(practiceTestService.submitAnswer(chatId, answerId));
                    return;
                }

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

                    Integer messageId =
                            update.getCallbackQuery()
                                    .getMessage()
                                    .getMessageId();

                    User pupil = getUserByChatId(chatId);

                    SyncAttemptRequestDto dto =
                            new SyncAttemptRequestDto(
                                    attemptId,
                                    List.of(new AnswerSyncDto(questionId, answerId))
                            );

                    assignmentAttemptService.syncAttempt(pupil, dto);

                    int nextIndex = index + 1;

                    List<Question> questions =
                            assignmentAttemptService.getQuestionsForAttempt(attemptId);

                    if (nextIndex >= questions.size()) {

                        assignmentAttemptService.finishTaskSession(pupil, attemptId);

                        // 1️⃣ редактируем последний вопрос
                        EditMessageText finishEdit = new EditMessageText();
                        finishEdit.setChatId(chatId.toString());
                        finishEdit.setMessageId(messageId);
                        finishEdit.setText("✅ Test yakunlandi!");

                        execute(finishEdit);

                        // 2️⃣ отправляем результат
                        SendMessage result =
                                telegramQuizService.sendFinishMessage(chatId, attemptId);

                        execute(result);

                    } else {

                        EditMessageText edit =
                                telegramQuizService.editQuestion(
                                        chatId,
                                        messageId,
                                        attemptId,
                                        nextIndex
                                );

                        execute(edit);
                    }
                }
                return;
            }

            if (update.hasMessage() && update.getMessage().hasText()) {
                Message msg = update.getMessage();
                String text = msg.getText().trim();
                Long chatId = msg.getChatId();
                SendMessage response = route(chatId, text, msg);

                if (response != null) execute(response);
            }
        } catch (Exception e) {
            if (isNetworkIssue(e)) {
                // api.telegram.org'ga vaqtincha ulanib bo'lmadi (ISP/tarmoq
                // beqarorligi) — o'zi keyingi update'da qayta uriniladi,
                // to'liq stack-trace shart emas, faqat qisqa ogohlantirish.
                log.warn("Telegram serveriga vaqtincha ulanib bo'lmadi (tarmoq beqarorligi): {}", e.getMessage());
            } else {
                log.error("Telegram update error", e);
            }
        }
    }

    // Matnli xabarni qayerga yo'naltirish kerakligini hal qiladi: /start,
    // /menu, /cancel maxsus buyruqlar; keyin joriy suhbat holati (masalan
    // "yangi email kutilmoqda") tekshiriladi; keyin asosiy menyu tugmalari;
    // aks holda eski (/link, /pay) buyruqlarga tushadi.
    private SendMessage route(Long chatId, String text, Message msg) {

        if (text.equals("/start")) {
            return handleStart(chatId);
        }

        if (text.equals("/menu")) {
            User user = telegramUserService.resolveLinkedUser(chatId);
            if (user == null) return notLinkedMessage(chatId);
            return menuMessage(user);
        }

        if (text.equals("/cancel")) {
            if (sessionService.getState(chatId) == BotState.IN_PRACTICE_TEST) {
                return practiceTestService.cancel(chatId);
            }
            return profileService.cancelFlow(chatId);
        }

        // Foydalanuvchi ko'p bosqichli oqim o'rtasida (masalan yangi
        // parolni kutyapmiz, yoki mustaqil test o'rtasida) — keyingi matn
        // menyu tugmasi emas, shu oqimning davomi sifatida ishlanadi.
        BotState state = sessionService.getState(chatId);
        if (state == BotState.IN_PRACTICE_TEST) {
            // Test paytida javob faqat inline tugmalar orqali tanlanadi —
            // matn yozilsa, shunchaki eslatib qo'yamiz.
            return practiceTestService.reminderToUseButtons(chatId);
        }
        if (state != BotState.NONE) {
            return profileService.handleAwaitingInput(chatId, state, text);
        }

        // Asosiy menyu tugmalari — hammasi ulangan (linklangan) akkauntni talab qiladi.
        if (isMainMenuButton(text)) {
            User user = telegramUserService.resolveLinkedUser(chatId);
            if (user == null) return notLinkedMessage(chatId);
            return handleMenuButton(text, user);
        }

        // Eski (hali menyuga aylantirilmagan) buyruqlar: /link, /pay va h.k.
        return telegramUserService.handleMessage(msg);
    }

    private SendMessage handleStart(Long chatId) {
        User user = telegramUserService.resolveLinkedUser(chatId);
        if (user == null) return notLinkedMessage(chatId);
        return menuMessage(user);
    }

    private SendMessage notLinkedMessage(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("Avval sayt orqali Telegramni ulang: saytda /profile sahifasida " +
                "\"Telegramga ulash\" tugmasini bosing va bergan kodni shu yerga " +
                "\"/link 123456\" ko'rinishida yuboring.");
        return msg;
    }

    private SendMessage menuMessage(User user) {
        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());
        msg.setText(menuService.welcomeText(user));
        msg.setReplyMarkup(menuService.buildMainMenu(user));
        return msg;
    }

    private boolean isMainMenuButton(String text) {
        return switch (text) {
            case BTN_PROFILE, BTN_NOTIFICATIONS, BTN_SUBSCRIPTION, BTN_COURSES, BTN_HELP,
                    BTN_MY_ASSIGNMENTS, BTN_MY_RESULTS, BTN_PRACTICE_TEST, BTN_MY_GROUPS, BTN_NEW_ASSIGNMENT,
                    BTN_QUESTIONS, BTN_USERS, BTN_PAYMENTS, BTN_SETTINGS, BTN_BROADCAST -> true;
            default -> false;
        };
    }

    private SendMessage handleMenuButton(String text, User user) {
        return switch (text) {
            case BTN_PROFILE -> profileService.viewProfile(user.getTelegramId());
            case BTN_NOTIFICATIONS -> menuService.showNotifications(user);
            case BTN_SUBSCRIPTION -> menuService.showSubscription(user);
            case BTN_COURSES -> menuService.showCourses(user);
            case BTN_HELP -> menuService.help(user);
            case BTN_MY_ASSIGNMENTS -> telegramUserService.sendMyAssignments(user.getTelegramId());
            case BTN_MY_RESULTS -> telegramUserService.sendMyResults(user.getTelegramId());
            case BTN_PRACTICE_TEST -> practiceTestService.startFlow(user.getTelegramId());
            // ROADMAP'dagi keyingi bosqichlar (ADMIN/OWNER'ga xos bo'limlar) —
            // hozircha "tez orada" javobi.
            default -> menuService.comingSoon(user.getTelegramId());
        };
    }

    // Sabab zanjirida tarmoq bilan bog'liq xatolik bormi (ulanish vaqti
    // tugashi, host topilmadi va h.k.) — bo'lsa, bu shunchaki vaqtinchalik
    // tarmoq muammosi, dastur xatosi emas.
    private boolean isNetworkIssue(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.io.IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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

    private @NotNull SendMessage getSendMessage(@NotNull Long chatId, @NotNull AttemptDto attempt) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (attempt.finishedAt() != null) {

            msg.setText("Bu test allaqachon yakunlangan. \nYakunlangan vaqt: "
                    + attempt.finishedAt().format(TelegramUserService.DATE_TIME_FORMATTER));
        } else {
            msg = telegramQuizService.resumeAttempt(
                    chatId,
                    attempt.attemptId()
            );
        }
        return msg;
    }
}