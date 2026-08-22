package behzoddev.testproject.telegram;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.student.AnswerSyncDto;
import behzoddev.testproject.dto.student.AttemptDto;
import behzoddev.testproject.dto.student.SyncAttemptRequestDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentAttemptService;
import behzoddev.testproject.service.NotificationService;
import behzoddev.testproject.telegram.service.TelegramAssignmentChatService;
import behzoddev.testproject.telegram.service.TelegramCourseReaderService;
import behzoddev.testproject.telegram.service.TelegramMenuService;
import behzoddev.testproject.telegram.service.TelegramOwnerService;
import behzoddev.testproject.telegram.service.TelegramPracticeTestService;
import behzoddev.testproject.telegram.service.TelegramProfileService;
import behzoddev.testproject.telegram.service.TelegramQuestionImportService;
import behzoddev.testproject.telegram.service.TelegramQuizService;
import behzoddev.testproject.telegram.service.TelegramRegistrationService;
import behzoddev.testproject.telegram.service.TelegramSessionService;
import behzoddev.testproject.telegram.service.TelegramTeacherService;
import behzoddev.testproject.telegram.service.TelegramUserService;
import behzoddev.testproject.telegram.state.BotState;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private final TelegramTeacherService teacherService;
    private final TelegramAssignmentChatService chatService;
    private final TelegramQuestionImportService questionImportService;
    private final TelegramOwnerService ownerService;
    private final TelegramRegistrationService registrationService;
    private final TelegramCourseReaderService courseReaderService;

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
            TelegramPracticeTestService practiceTestService,
            TelegramTeacherService teacherService,
            TelegramAssignmentChatService chatService,
            TelegramQuestionImportService questionImportService,
            TelegramOwnerService ownerService,
            TelegramRegistrationService registrationService,
            TelegramCourseReaderService courseReaderService) {
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
        this.teacherService = teacherService;
        this.chatService = chatService;
        this.questionImportService = questionImportService;
        this.ownerService = ownerService;
        this.registrationService = registrationService;
        this.courseReaderService = courseReaderService;
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

                // ===== Kurslarni botda o'qish (obuna bor yoki bepul bo'lsa) =====
                // Har bir navigatsiya qadamida (ro'yxat -> kurs -> mavzular ->
                // mavzu -> keyingi mavzu) OLDINGI xabar o'chiriladi — aks holda
                // "Keyingi mavzu" bosilgan sayin chatda o'nlab eski xabar
                // to'planib qolardi. Yangi xabar xuddi shu o'rinda ochilgandek
                // tuyuladi.
                Integer courseMsgId = update.getCallbackQuery().getMessage().getMessageId();

                if (data.equals("course_list")) {
                    deleteMessageSafely(chatId, courseMsgId);
                    execute(courseReaderService.showCourseList(getUserByChatId(chatId)));
                    return;
                }
                if (data.startsWith("course_open_")) {
                    Long courseId = Long.parseLong(data.replace("course_open_", ""));
                    deleteMessageSafely(chatId, courseMsgId);
                    execute(courseReaderService.openCourse(getUserByChatId(chatId), courseId));
                    return;
                }
                // ===== Kursga Click orqali onlayn to'lash (OWNER tasdig'ini kutmasdan) =====
                if (data.startsWith("course_pay_")) {
                    Long courseId = Long.parseLong(data.replace("course_pay_", ""));
                    execute(courseReaderService.payWithClick(getUserByChatId(chatId), courseId));
                    return;
                }
                // ===== Kursga obuna so'rovi (OWNER qo'lda tasdiqlaydi) — botning o'zidan =====
                if (data.startsWith("course_request_")) {
                    Long courseId = Long.parseLong(data.replace("course_request_", ""));
                    execute(courseReaderService.requestSubscription(getUserByChatId(chatId), courseId));
                    return;
                }
                if (data.startsWith("course_secs_")) {
                    String rest = data.replace("course_secs_", "");
                    int sep = rest.lastIndexOf('_');
                    Long courseId = Long.parseLong(rest.substring(0, sep));
                    int page = Integer.parseInt(rest.substring(sep + 1));
                    deleteMessageSafely(chatId, courseMsgId);
                    execute(courseReaderService.showSectionsPage(getUserByChatId(chatId), courseId, page));
                    return;
                }
                if (data.startsWith("course_sec_")) {
                    String rest = data.replace("course_sec_", "");
                    int sep = rest.indexOf('_');
                    Long courseId = Long.parseLong(rest.substring(0, sep));
                    Long sectionId = Long.parseLong(rest.substring(sep + 1));
                    User sectionUser = getUserByChatId(chatId);
                    deleteMessageSafely(chatId, courseMsgId);
                    for (SendMessage m : courseReaderService.openSection(sectionUser, courseId, sectionId)) {
                        execute(m);
                    }
                    for (SendDocument d : courseReaderService.documentsForSection(sectionUser, courseId, sectionId)) {
                        execute(d);
                    }
                    return;
                }
                if (data.startsWith("course_complete_")) {
                    String rest = data.replace("course_complete_", "");
                    int sep = rest.indexOf('_');
                    Long courseId = Long.parseLong(rest.substring(0, sep));
                    Long sectionId = Long.parseLong(rest.substring(sep + 1));
                    deleteMessageSafely(chatId, courseMsgId);
                    for (SendMessage m : courseReaderService.completeAndAdvance(getUserByChatId(chatId), courseId, sectionId)) {
                        execute(m);
                    }
                    return;
                }
                // ===== Kurs bo'limidagi mavzuga oid testni botning o'zida yechish =====
                if (data.startsWith("course_test_")) {
                    Long topicId = Long.parseLong(data.replace("course_test_", ""));
                    execute(practiceTestService.startForTopic(chatId, topicId));
                    return;
                }

                // ===== Mustaqil test (rejim -> fan -> savol soni -> savol-javob) =====
                if (data.startsWith("pt_mode_")) {
                    String mode = data.replace("pt_mode_", "");
                    execute(practiceTestService.selectMode(chatId, mode));
                    return;
                }
                if (data.startsWith("pt_science_")) {
                    Long scienceId = Long.parseLong(data.replace("pt_science_", ""));
                    execute(practiceTestService.selectScience(chatId, scienceId));
                    return;
                }
                if (data.equals("pt_count_custom")) {
                    execute(practiceTestService.promptCustomCount(chatId));
                    return;
                }
                if (data.startsWith("pt_count_")) {
                    int count = Integer.parseInt(data.replace("pt_count_", ""));
                    execute(practiceTestService.chooseCount(chatId, count));
                    return;
                }
                if (data.equals("pt_time_custom")) {
                    execute(practiceTestService.promptCustomTimeLimit(chatId));
                    return;
                }
                if (data.startsWith("pt_time_")) {
                    int minutes = Integer.parseInt(data.replace("pt_time_", ""));
                    execute(practiceTestService.applyTimeLimit(chatId, minutes));
                    return;
                }
                if (data.startsWith("pt_answer_")) {
                    Long answerId = Long.parseLong(data.replace("pt_answer_", ""));
                    execute(practiceTestService.submitAnswer(chatId, answerId));
                    return;
                }

                // ===== ADMIN: Gruppalar =====
                if (data.startsWith("tg_group_")) {
                    Long groupId = Long.parseLong(data.replace("tg_group_", ""));
                    execute(teacherService.viewGroup(chatId, groupId));
                    return;
                }
                if (data.equals("tg_newgroup")) {
                    execute(teacherService.startCreateGroup(chatId));
                    return;
                }
                if (data.startsWith("tg_invite_")) {
                    Long groupId = Long.parseLong(data.replace("tg_invite_", ""));
                    execute(teacherService.startInvite(chatId, groupId));
                    return;
                }

                // ===== ADMIN: Topshiriq berish =====
                if (data.startsWith("tg_assign_group_")) {
                    Long groupId = Long.parseLong(data.replace("tg_assign_group_", ""));
                    execute(teacherService.selectAssignGroup(chatId, groupId));
                    return;
                }
                if (data.startsWith("tg_assign_set_")) {
                    Long setId = Long.parseLong(data.replace("tg_assign_set_", ""));
                    execute(teacherService.selectAssignSet(chatId, setId));
                    return;
                }
                if (data.startsWith("tg_assign_due_")) {
                    int days = Integer.parseInt(data.replace("tg_assign_due_", ""));
                    execute(teacherService.finalizeAssign(chatId, days));
                    return;
                }

                // ===== ADMIN: Natijalar =====
                if (data.startsWith("tg_result_")) {
                    Long assignmentId = Long.parseLong(data.replace("tg_result_", ""));
                    execute(teacherService.showResultDetail(chatId, assignmentId));
                    return;
                }

                // ===== ADMIN: Topshiriq chatlari =====
                if (data.startsWith("tg_chat_")) {
                    Long assignmentId = Long.parseLong(data.replace("tg_chat_", ""));
                    execute(chatService.showChat(chatId, assignmentId));
                    return;
                }

                // ===== ADMIN: Savollar boshqaruvi (Excel import) =====
                if (data.startsWith("tg_import_science_")) {
                    Long scienceId = Long.parseLong(data.replace("tg_import_science_", ""));
                    execute(questionImportService.selectScience(chatId, scienceId));
                    return;
                }
                if (data.startsWith("tg_import_topic_")) {
                    Long topicId = Long.parseLong(data.replace("tg_import_topic_", ""));
                    execute(questionImportService.selectTopic(chatId, topicId));
                    return;
                }
                if (data.equals("tg_import_template")) {
                    execute(questionImportService.sendTemplate(chatId));
                    return;
                }

                // ===== OWNER: Foydalanuvchilar (rol berish/olib tashlash, blokdan chiqarish) =====
                if (data.startsWith("tg_roleadd_") || data.startsWith("tg_roledel_")) {
                    boolean add = data.startsWith("tg_roleadd_");
                    String rest = data.replace(add ? "tg_roleadd_" : "tg_roledel_", "");
                    int sep = rest.indexOf('_');
                    Long targetUserId = Long.parseLong(rest.substring(0, sep));
                    String roleName = rest.substring(sep + 1);
                    execute(ownerService.toggleRole(chatId, targetUserId, roleName, add));
                    return;
                }
                if (data.startsWith("tg_unlock_")) {
                    Long targetUserId = Long.parseLong(data.replace("tg_unlock_", ""));
                    execute(ownerService.unlockUser(chatId, targetUserId));
                    return;
                }

                // ===== OWNER: To'lovlar =====
                if (data.startsWith("tg_paydetail_")) {
                    Long subscriptionId = Long.parseLong(data.replace("tg_paydetail_", ""));
                    execute(ownerService.showPaymentDetail(chatId, subscriptionId));
                    return;
                }
                if (data.startsWith("tg_payok_")) {
                    Long subscriptionId = Long.parseLong(data.replace("tg_payok_", ""));
                    execute(ownerService.confirmPayment(chatId, subscriptionId));
                    return;
                }
                if (data.startsWith("tg_payno_")) {
                    Long subscriptionId = Long.parseLong(data.replace("tg_payno_", ""));
                    execute(ownerService.rejectPayment(chatId, subscriptionId));
                    return;
                }

                // ===== OWNER: Tizim sozlamalari =====
                if (data.equals("tg_settings_edit")) {
                    execute(ownerService.startEditMinAmount(chatId));
                    return;
                }

                // ===== OWNER: E'lon yuborish =====
                if (data.equals("tg_broadcast_yes")) {
                    for (SendMessage m : ownerService.buildBroadcastMessages(chatId)) {
                        try {
                            execute(m);
                        } catch (Exception e) {
                            log.warn("E'lonni yuborib bo'lmadi: chatId={}", m.getChatId(), e);
                        }
                    }
                    return;
                }
                if (data.equals("tg_broadcast_no")) {
                    execute(ownerService.cancelBroadcast(chatId));
                    return;
                }

                // ===== Botda ro'yxatdan o'tish =====
                if (data.equals("reg_start")) {
                    execute(registrationService.start(chatId));
                    return;
                }
                if (data.equals("reg_skip_phone")) {
                    execute(registrationService.skipPhone(chatId));
                    return;
                }
                if (data.equals("reg_terms_yes")) {
                    execute(registrationService.confirmTerms(chatId));
                    return;
                }
                if (data.equals("reg_terms_no")) {
                    execute(registrationService.cancelTerms(chatId));
                    return;
                }
                if (data.equals("reg_resend_code")) {
                    execute(registrationService.resendCode(chatId));
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

            if (update.hasMessage() && update.getMessage().hasDocument()) {
                Long chatId = update.getMessage().getChatId();
                SendMessage response = handleDocument(chatId, update.getMessage().getDocument());
                if (response != null) execute(response);
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
            BotState currentState = sessionService.getState(chatId);
            if (currentState == BotState.IN_PRACTICE_TEST || currentState == BotState.AWAITING_PT_CUSTOM_COUNT
                    || currentState == BotState.AWAITING_PT_CUSTOM_TIME) {
                return practiceTestService.cancel(chatId);
            }
            return profileService.cancelFlow(chatId);
        }

        // Foydalanuvchi ko'p bosqichli oqim o'rtasida (masalan yangi
        // parolni kutyapmiz, yoki mustaqil test o'rtasida) — keyingi matn
        // menyu tugmasi emas, shu oqimning davomi sifatida ishlanadi.
        BotState state = sessionService.getState(chatId);
        SendMessage flowResponse = routeAwaitingState(chatId, state, text);
        if (flowResponse != null) return flowResponse;

        // Asosiy menyu tugmalari — hammasi ulangan (linklangan) akkauntni talab qiladi.
        if (isMainMenuButton(text)) {
            User user = telegramUserService.resolveLinkedUser(chatId);
            if (user == null) return notLinkedMessage(chatId);
            return handleMenuButton(text, user);
        }

        // Eski (hali menyuga aylantirilmagan) buyruqlar: /link, /pay va h.k.
        return telegramUserService.handleMessage(msg);
    }

    // "🗂 Savollar boshqaruvi" oqimida (mavzu tanlangandan keyin) foydalanuvchi
    // .xlsx faylni to'g'ridan-to'g'ri chatga yuborsa — shu yerda ishlanadi.
    // Boshqa har qanday holatda kelgan hujjat e'tiborsiz qoldiriladi (eslatma bilan).
    private SendMessage handleDocument(Long chatId, Document document) {
        if (sessionService.getState(chatId) != BotState.AWAITING_EXCEL_FILE) {
            return questionImportService.notWaitingForFile(chatId);
        }

        java.io.File tempFile = null;
        try {
            org.telegram.telegrambots.meta.api.objects.File telegramFile =
                    execute(GetFile.builder().fileId(document.getFileId()).build());
            tempFile = downloadFile(telegramFile);
            byte[] bytes = Files.readAllBytes(tempFile.toPath());
            return questionImportService.importFile(chatId, bytes, document.getFileName());
        } catch (TelegramApiException | IOException e) {
            log.error("Excel faylni yuklab olishda xatolik", e);
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Faylni yuklab olishda xatolik yuz berdi. Qaytadan urinib ko'ring.");
            return msg;
        } finally {
            if (tempFile != null) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    // Joriy suhbat holatiga qarab, keyingi matnni tegishli servisga
    // yo'naltiradi. NONE bo'lsa — null (chaqiruvchi asosiy menyu
    // tugmalarini tekshirishga o'tadi).
    private SendMessage routeAwaitingState(Long chatId, BotState state, String text) {
        return switch (state) {
            case NONE -> null;
            case IN_PRACTICE_TEST -> practiceTestService.reminderToUseButtons(chatId);
            case AWAITING_PT_CUSTOM_COUNT -> practiceTestService.applyCustomCount(chatId, text);
            case AWAITING_PT_CUSTOM_TIME -> practiceTestService.applyCustomTimeLimit(chatId, text);
            case AWAITING_GROUP_NAME -> teacherService.applyGroupName(chatId, text);
            case AWAITING_INVITE_USERNAME -> teacherService.applyInviteUsername(chatId, text);
            case AWAITING_CHAT_MESSAGE -> chatService.sendReply(chatId, text);
            case AWAITING_EXCEL_FILE -> questionImportService.remindToSendFile(chatId);
            case AWAITING_USER_SEARCH -> ownerService.applyUserSearch(chatId, text);
            case AWAITING_MIN_AMOUNT -> ownerService.applyMinAmount(chatId, text);
            case AWAITING_BROADCAST_TEXT -> ownerService.previewBroadcast(chatId, text);
            case AWAITING_REG_USERNAME -> registrationService.applyUsername(chatId, text);
            case AWAITING_REG_EMAIL -> registrationService.applyEmail(chatId, text);
            case AWAITING_REG_PHONE -> registrationService.applyPhone(chatId, text);
            case AWAITING_REG_PASSWORD -> registrationService.applyPassword(chatId, text);
            case AWAITING_REG_CONFIRM_PASSWORD -> registrationService.applyConfirmPassword(chatId, text);
            case AWAITING_REG_TERMS -> registrationService.remindToTapTermsButton(chatId);
            case AWAITING_REG_EMAIL_CODE -> registrationService.applyEmailCode(chatId, text);
            default -> profileService.handleAwaitingInput(chatId, state, text);
        };
    }

    private SendMessage handleStart(Long chatId) {
        User user = telegramUserService.resolveLinkedUser(chatId);
        if (user == null) return notLinkedMessage(chatId);
        return menuMessage(user);
    }

    private SendMessage notLinkedMessage(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText("👋 Xush kelibsiz!\n\n" +
                "Agar saytda hisobingiz hali yo'q bo'lsa — pastdagi tugma orqali " +
                "to'g'ridan-to'g'ri shu yerda ro'yxatdan o'tishingiz mumkin.\n\n" +
                "Agar hisobingiz allaqachon bor bo'lsa — saytda /profile sahifasida " +
                "\"Telegramga ulash\" tugmasini bosib, bergan kodni shu yerga " +
                "\"/link 123456\" ko'rinishida yuboring.");

        InlineKeyboardButton registerBtn = new InlineKeyboardButton();
        registerBtn.setText("🆕 Ro'yxatdan o'tish");
        registerBtn.setCallbackData("reg_start");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(registerBtn)));
        msg.setReplyMarkup(markup);
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
                    BTN_STUDENT_RESULTS, BTN_QUESTIONS, BTN_ASSIGNMENT_CHATS,
                    BTN_USERS, BTN_PAYMENTS, BTN_SETTINGS, BTN_BROADCAST -> true;
            default -> false;
        };
    }

    private SendMessage handleMenuButton(String text, User user) {
        return switch (text) {
            case BTN_PROFILE -> profileService.viewProfile(user.getTelegramId());
            case BTN_NOTIFICATIONS -> menuService.showNotifications(user);
            case BTN_SUBSCRIPTION -> menuService.showSubscription(user);
            case BTN_COURSES -> courseReaderService.showCourseList(user);
            case BTN_HELP -> menuService.help(user);
            case BTN_MY_ASSIGNMENTS -> telegramUserService.sendMyAssignments(user.getTelegramId());
            case BTN_MY_RESULTS -> telegramUserService.sendMyResults(user.getTelegramId());
            case BTN_PRACTICE_TEST -> practiceTestService.startFlow(user.getTelegramId());
            case BTN_MY_GROUPS -> teacherService.listGroups(user);
            case BTN_NEW_ASSIGNMENT -> teacherService.startAssignFlow(user);
            case BTN_STUDENT_RESULTS -> teacherService.listResults(user);
            case BTN_QUESTIONS -> questionImportService.startFlow(user.getTelegramId());
            case BTN_ASSIGNMENT_CHATS -> chatService.listAssignments(user);
            case BTN_USERS -> ownerService.startUserSearch(user.getTelegramId());
            case BTN_PAYMENTS -> ownerService.listPendingPayments(user);
            case BTN_SETTINGS -> ownerService.showSettings(user.getTelegramId());
            case BTN_BROADCAST -> ownerService.startBroadcast(user.getTelegramId());
            default -> menuService.comingSoon(user);
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

    // Kurs bo'limlarini o'qishda navigatsiya qadamlarida oldingi xabarni
    // olib tashlash uchun. Xato bo'lsa (masalan xabar 48 soatdan eski —
    // Telegram bunday xabarlarni o'chirishga ruxsat bermaydi, yoki
    // foydalanuvchi allaqachon o'chirib yuborgan) — jim o'tkazib yuboramiz,
    // bu funksional emas, faqat estetik tozalik uchun.
    private void deleteMessageSafely(Long chatId, Integer messageId) {
        try {
            execute(new DeleteMessage(chatId.toString(), messageId));
        } catch (Exception e) {
            log.debug("Eski xabarni o'chirib bo'lmadi: chatId={}, messageId={}", chatId, messageId, e);
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