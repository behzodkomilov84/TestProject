package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSectionContentDto;
import behzoddev.testproject.dto.course.CourseSectionSummaryDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.service.CourseService;
import behzoddev.testproject.service.CourseSubscriptionService;
import behzoddev.testproject.service.payment.ClickService;
import behzoddev.testproject.service.payment.PaymentOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Botda "📚 Kurslar" — kurslarni Telegram ichida o'qish (obuna bor yoki
 * bepul bo'lsa). Asosiy e'tibor: ruxsat tekshiruvi (CourseService orqali),
 * sahifalash va HTML->Telegram HTML konvertatsiyasi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramCourseReaderServiceTest {

    @Mock
    private CourseService courseService;
    @Mock
    private PaymentOrderService paymentOrderService;
    @Mock
    private ClickService clickService;
    @Mock
    private CourseSubscriptionService courseSubscriptionService;

    @InjectMocks
    private TelegramCourseReaderService courseReaderService;

    @TempDir
    private Path tempUploadDir;

    private User student() {
        Role role = Role.builder().id(1L).roleName("ROLE_USER").build();
        return User.builder().id(1L).username("student").telegramId(100L)
                .roles(new HashSet<>(Set.of(role))).build();
    }

    @BeforeEach
    void setUp() {
        // Bot faylni Telegram'ga havoladan emas, diskdan o'qib yuboradi
        // (production'dagi "failed to get HTTP URL content" xatosining
        // tuzatilishi) — shuning uchun testda ham haqiqiy faylga ehtiyoj bor.
        ReflectionTestUtils.setField(courseReaderService, "uploadDir", tempUploadDir.toString());
        // Aksariyat testlar to'lov bilan bog'liq emas — standart holatda
        // Click o'chirilgan deb hisoblanadi ("💳 to'lash" tugmasi chiqmaydi).
        org.mockito.Mockito.lenient().when(clickService.isEnabled()).thenReturn(false);
    }

    private void createUploadedFile(String relativePath) {
        try {
            Path file = tempUploadDir.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "stub content");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ===== showCourseList =====

    @Test
    void showCourseList_none_saysEmpty() {
        User user = student();
        when(courseService.listCatalog(user)).thenReturn(List.of());

        SendMessage msg = courseReaderService.showCourseList(user);

        assertThat(msg.getText()).contains("kurslar mavjud emas");
    }

    @Test
    void showCourseList_freeCourse_showsFreeIcon() {
        User user = student();
        CourseDto course = CourseDto.builder().id(1L).title("Bepul kurs").published(true)
                .free(true).subscribed(true).sectionCount(3).build();
        when(courseService.listCatalog(user)).thenReturn(List.of(course));

        SendMessage msg = courseReaderService.showCourseList(user);

        // To'liq nom xabar MATNIDA (chapga tekislangan, o'zi qatorlarga
        // bo'linadigan) ko'rinadi; tugma esa faqat kichik belgi+raqam —
        // Telegram tugma matnini markazlashtirib, bitta qatorga
        // kesib qo'yishi (uzun nomlar to'liq ko'rinmasligi) shu bilan oldini olinadi.
        assertThat(msg.getText()).contains("🆓 1. Bepul kurs");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        assertThat(markup.getKeyboard().get(0).get(0).getText()).isEqualTo("🆓 1");
        assertThat(markup.getKeyboard().get(0).get(0).getCallbackData()).isEqualTo("course_open_1");
    }

    @Test
    void showCourseList_lockedCourse_showsLockIcon() {
        User user = student();
        CourseDto course = CourseDto.builder().id(2L).title("Pullik kurs").published(true)
                .free(false).subscribed(false).sectionCount(3).build();
        when(courseService.listCatalog(user)).thenReturn(List.of(course));

        SendMessage msg = courseReaderService.showCourseList(user);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        assertThat(markup.getKeyboard().get(0).get(0).getText()).isEqualTo("🔒 1");
    }

    @Test
    void showCourseList_lockedCourseWithPrice_showsPriceInText() {
        User user = student();
        CourseDto course = CourseDto.builder().id(2L).title("Pullik kurs").published(true)
                .free(false).subscribed(false).price(new java.math.BigDecimal("150000")).sectionCount(3).build();
        when(courseService.listCatalog(user)).thenReturn(List.of(course));

        SendMessage msg = courseReaderService.showCourseList(user);

        assertThat(msg.getText()).contains("150 000 so'm");
    }

    // ===== openCourse (ruxsat tekshiruvi) =====

    @Test
    void openCourse_notSubscribedAndNotFree_showsAccessDeniedWithRequestButton() {
        User user = student();
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Kimyo").published(true)
                .free(false).subscribed(false).canManage(false).sections(List.of()).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);

        SendMessage msg = courseReaderService.openCourse(user, 5L);

        // Endi saytga o'tkazuvchi havola emas — so'rov to'g'ridan-to'g'ri
        // botning o'zidan (tugma bilan) yuboriladi.
        assertThat(msg.getText()).contains("obuna kerak").doesNotContain("study-grow.uz");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        boolean hasRequestButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> "course_request_5".equals(b.getCallbackData()));
        assertThat(hasRequestButton).isTrue();
    }

    @Test
    void openCourse_requestAlreadyPending_showsWaitingMessageWithoutRequestButton() {
        User user = student();
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Kimyo").published(true)
                .free(false).subscribed(false).canManage(false).requestPending(true).sections(List.of()).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);

        SendMessage msg = courseReaderService.openCourse(user, 5L);

        assertThat(msg.getText()).contains("so'rovingiz allaqachon yuborilgan");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        boolean hasRequestButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> "course_request_5".equals(b.getCallbackData()));
        assertThat(hasRequestButton).isFalse();
    }

    @Test
    void openCourse_clickEnabledWithPrice_showsPayButton() {
        User user = student();
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Kimyo").published(true)
                .free(false).subscribed(false).canManage(false)
                .price(new java.math.BigDecimal("50000")).sections(List.of()).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);
        when(clickService.isEnabled()).thenReturn(true);

        SendMessage msg = courseReaderService.openCourse(user, 5L);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        boolean hasPayButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> "course_pay_5".equals(b.getCallbackData()));
        assertThat(hasPayButton).isTrue();
    }

    @Test
    void openCourse_clickDisabled_hidesPayButtonEvenWithPrice() {
        User user = student();
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Kimyo").published(true)
                .free(false).subscribed(false).canManage(false)
                .price(new java.math.BigDecimal("50000")).sections(List.of()).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);

        SendMessage msg = courseReaderService.openCourse(user, 5L);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        boolean hasPayButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> b.getCallbackData() != null && b.getCallbackData().startsWith("course_pay_"));
        assertThat(hasPayButton).isFalse();
    }

    @Test
    void openCourse_notSubscribedWithPrice_showsPriceInAccessDeniedMessage() {
        User user = student();
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Kimyo").published(true)
                .free(false).subscribed(false).canManage(false)
                .price(new java.math.BigDecimal("200000")).sections(List.of()).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);

        SendMessage msg = courseReaderService.openCourse(user, 5L);

        assertThat(msg.getText()).contains("200 000 so'm");
    }

    @Test
    void openCourse_free_showsSectionsListDirectly() {
        User user = student();
        String longTitle = "Juda uzun mavzu nomi — bu nom bitta inline tugmaga sig'may qolishi mumkin bo'lgan uzunlikda";
        CourseSectionSummaryDto section = CourseSectionSummaryDto.builder()
                .id(10L).title(longTitle).orderIndex(1).type("TEXT").locked(false).completed(false).build();
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Bepul kurs").published(true)
                .free(true).subscribed(true).canManage(false).sections(List.of(section)).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);

        SendMessage msg = courseReaderService.openCourse(user, 5L);

        // To'liq (uzun) nom xabar matnida butunligicha ko'rinadi.
        assertThat(msg.getText()).contains("Bepul kurs").contains("Mavzuni tanlang").contains(longTitle);
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        // Tugma matni endi faqat kompakt belgi+raqam — uzun nomni o'zida saqlamaydi.
        assertThat(markup.getKeyboard().get(0).get(0).getText()).isEqualTo("▫️ 1");
        assertThat(markup.getKeyboard().get(0).get(0).getCallbackData()).isEqualTo("course_sec_5_10");
    }

    @Test
    void openCourse_courseNotFound_showsNotFoundMessage() {
        User user = student();
        when(courseService.getDetail(99L, user)).thenThrow(new NoSuchElementException("Kurs topilmadi"));

        SendMessage msg = courseReaderService.openCourse(user, 99L);

        assertThat(msg.getText()).contains("topilmadi");
    }

    // ===== showSectionsPage (sahifalash) =====

    @Test
    void showSectionsPage_moreThanOnePage_showsNavigationButtons() {
        User user = student();
        List<CourseSectionSummaryDto> sections = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            sections.add(CourseSectionSummaryDto.builder()
                    .id((long) i).title(i + "-mavzu").orderIndex(i).type("TEXT")
                    .locked(false).completed(false).build());
        }
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Kurs").published(true)
                .free(true).subscribed(true).canManage(false).sections(sections).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);

        SendMessage page0 = courseReaderService.showSectionsPage(user, 5L, 0);

        // 8 tadan sahifalanadi — 10 ta bo'lim = 2 sahifa, 0-sahifada faqat "▶️" bo'lishi kerak.
        // Tugmalar endi yonma-yon (4 tadan bir qatorda): 8 ta mavzu = 2 qator.
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) page0.getReplyMarkup();
        List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
        assertThat(rows).hasSize(2 + 1 + 1); // 2 qator mavzu tugmasi + navigatsiya qatori + "orqaga"
        assertThat(rows.get(0)).hasSize(4);
        assertThat(rows.get(1)).hasSize(4);
        assertThat(page0.getText()).contains("1/2-sahifa");
    }

    // ===== openSection (ruxsat, avtomatik tugatish, navigatsiya) =====

    @Test
    void openSection_locked_returnsAccessDeniedMessage() {
        User user = student();
        when(courseService.getSectionContent(5L, 10L, user))
                .thenThrow(new AccessDeniedException("⛔ Bu bo'lim hali ochilmagan."));

        List<SendMessage> messages = courseReaderService.openSection(user, 5L, 10L);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getText()).contains("ochilmagan");
    }

    @Test
    void openSection_notFound_returnsNotFoundMessage() {
        User user = student();
        when(courseService.getSectionContent(5L, 10L, user))
                .thenThrow(new NoSuchElementException("Bo'lim topilmadi"));

        List<SendMessage> messages = courseReaderService.openSection(user, 5L, 10L);

        assertThat(messages.get(0).getText()).contains("topilmadi");
    }

    @Test
    void openSection_textType_autoCompletesAndRefreshesContent() {
        User user = student();
        CourseSectionContentDto beforeComplete = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("1-mavzu").orderIndex(1).type("TEXT")
                .textContent("Matn").textContentFormat("PLAIN").completed(false)
                .nextSectionId(11L).nextUnlocked(false).build();
        CourseSectionContentDto afterComplete = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("1-mavzu").orderIndex(1).type("TEXT")
                .textContent("Matn").textContentFormat("PLAIN").completed(true)
                .nextSectionId(11L).nextUnlocked(true).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(beforeComplete, afterComplete);

        List<SendMessage> messages = courseReaderService.openSection(user, 5L, 10L);

        org.mockito.Mockito.verify(courseService).markSectionCompleted(5L, 10L, user);
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) messages.get(messages.size() - 1).getReplyMarkup();
        boolean hasNextButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> "Keyingi ➡️".equals(b.getText()));
        assertThat(hasNextButton).isTrue();
    }

    @Test
    void openSection_videoType_showsCompleteButtonWhenNotCompleted() {
        User user = student();
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Video mavzu").orderIndex(1).type("VIDEO")
                .videoSourceType("YOUTUBE").videoUrl("abc123").completed(false).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        List<SendMessage> messages = courseReaderService.openSection(user, 5L, 10L);

        // VIDEO — TEXT'dan farqli, avtomatik tugatilmaydi.
        org.mockito.Mockito.verify(courseService, org.mockito.Mockito.never())
                .markSectionCompleted(any(), any(), any());
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) messages.get(0).getReplyMarkup();
        boolean hasCompleteButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> "✅ Tugatdim".equals(b.getText()));
        assertThat(hasCompleteButton).isTrue();
        assertThat(messages.get(0).getText()).contains("youtu.be/abc123");
    }

    @Test
    void openSection_linkedTopic_showsInBotTestButton() {
        User user = student();
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Mavzu").orderIndex(1).type("TEXT")
                .textContent("Matn").textContentFormat("PLAIN").completed(true)
                .linkedScienceId(3L).linkedTopicId(7L).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        List<SendMessage> messages = courseReaderService.openSection(user, 5L, 10L);

        // Saytga o'tkazuvchi URL emas — botning o'zida testni boshlaydigan callback.
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) messages.get(0).getReplyMarkup();
        boolean hasTestButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> "course_test_7".equals(b.getCallbackData()));
        assertThat(hasTestButton).isTrue();
    }

    // ===== completeAndAdvance =====

    @Test
    void completeAndAdvance_marksCompletedThenReopens() {
        User user = student();
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Video mavzu").orderIndex(1).type("VIDEO")
                .videoSourceType("YOUTUBE").videoUrl("abc").completed(true).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        courseReaderService.completeAndAdvance(user, 5L, 10L);

        org.mockito.Mockito.verify(courseService).markSectionCompleted(5L, 10L, user);
    }

    // ===== documentsForSection ("📥 Kitobni to'liq yuklab olish" kabi
    // PDF/DOCX havolalari — bosilganda tashqi brauzerga o'tkazish o'rniga,
    // fayl to'g'ridan-to'g'ri botning o'zida hujjat sifatida yuboriladi) =====

    @Test
    void documentsForSection_pdfAndDocxLinksInContent_sendsBothAsDocuments() {
        createUploadedFile("courses/kimyo-qollanma-toliq.pdf");
        createUploadedFile("courses/kimyo-qollanma-toliq.docx");

        User user = student();
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Kirish").orderIndex(1).type("TEXT")
                .textContent("📥 KITOBNI TO'LIQ YUKLAB OLISH\n" +
                        "PDF: https://study-grow.uz/uploads/courses/kimyo-qollanma-toliq.pdf\n" +
                        "Word: https://study-grow.uz/uploads/courses/kimyo-qollanma-toliq.docx")
                .textContentFormat("PLAIN").completed(true).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        List<org.telegram.telegrambots.meta.api.methods.send.SendDocument> documents =
                courseReaderService.documentsForSection(user, 5L, 10L);

        // Fayl endi havoladan (Telegram serveri o'zi yuklab olishiga
        // ishonib) emas, diskdan to'g'ridan-to'g'ri o'qib yuboriladi.
        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).getChatId()).isEqualTo("100");
        assertThat(documents.get(0).getDocument().isNew()).isTrue();
    }

    @Test
    void documentsForSection_fileMissingOnDisk_skipsItInsteadOfFailing() {
        // Fayl diskda yo'q — production'dagi "failed to get HTTP URL
        // content" kabi Telegram xatosini oldindan tutib, shu havolani
        // shunchaki o'tkazib yuborishi kerak (butun so'rovni buzmasdan).
        User user = student();
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Kirish").orderIndex(1).type("TEXT")
                .textContent("PDF: https://study-grow.uz/uploads/courses/yoq-fayl.pdf")
                .textContentFormat("PLAIN").completed(true).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        List<org.telegram.telegrambots.meta.api.methods.send.SendDocument> documents =
                courseReaderService.documentsForSection(user, 5L, 10L);

        assertThat(documents).isEmpty();
    }

    @Test
    void documentsForSection_noFileLinks_returnsEmpty() {
        User user = student();
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Oddiy mavzu").orderIndex(1).type("TEXT")
                .textContent("Bu yerda hech qanday fayl havolasi yo'q.")
                .textContentFormat("PLAIN").completed(true).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        List<org.telegram.telegrambots.meta.api.methods.send.SendDocument> documents =
                courseReaderService.documentsForSection(user, 5L, 10L);

        assertThat(documents).isEmpty();
    }

    @Test
    void documentsForSection_sectionNotAccessible_returnsEmptyInsteadOfThrowing() {
        User user = student();
        when(courseService.getSectionContent(5L, 10L, user))
                .thenThrow(new AccessDeniedException("⛔ Bu bo'lim hali ochilmagan."));

        List<org.telegram.telegrambots.meta.api.methods.send.SendDocument> documents =
                courseReaderService.documentsForSection(user, 5L, 10L);

        assertThat(documents).isEmpty();
    }

    // ===== toTelegramHtml (HTML -> Telegram HTML konvertatsiyasi) =====

    @Test
    void toTelegramHtml_plainFormat_escapesHtmlSpecialChars() {
        String result = courseReaderService.toTelegramHtml("PLAIN", "5 < 10 & 10 > 5");

        assertThat(result).isEqualTo("5 &lt; 10 &amp; 10 &gt; 5");
    }

    @Test
    void toTelegramHtml_htmlFormat_convertsParagraphsToNewlinesAndKeepsBold() {
        String result = courseReaderService.toTelegramHtml("HTML", "<p>Birinchi</p><p><strong>Ikkinchi</strong></p>");

        assertThat(result).isEqualTo("Birinchi\n<strong>Ikkinchi</strong>");
    }

    @Test
    void toTelegramHtml_htmlFormat_convertsListItemsToBullets() {
        String result = courseReaderService.toTelegramHtml("HTML", "<ul><li>Bir</li><li>Ikki</li></ul>");

        assertThat(result).isEqualTo("• Bir\n• Ikki");
    }

    @Test
    void toTelegramHtml_htmlFormat_flattensTableToPipeSeparatedRows() {
        String result = courseReaderService.toTelegramHtml("HTML",
                "<table><tr><td>A</td><td>B</td></tr><tr><td>C</td><td>D</td></tr></table>");

        assertThat(result).isEqualTo("A | B | \nC | D |");
    }

    @Test
    void toTelegramHtml_htmlFormat_stripsUnsupportedTagsButKeepsText() {
        String result = courseReaderService.toTelegramHtml("HTML",
                "<span style=\"color:red\">Qizil matn</span> <img src=\"x.png\">");

        assertThat(result).isEqualTo("Qizil matn");
    }

    @Test
    void toTelegramHtml_htmlFormat_keepsStrikeAndDelTags() {
        // Chrome'da execCommand('strikeThrough') <s> emas, <strike> tegini
        // chiqaradi — Telegram HTML rejimi ikkalasini ham qo'llab-quvvatlaydi.
        String result = courseReaderService.toTelegramHtml("HTML",
                "<strike>Eski</strike> <del>narx</del>");

        assertThat(result).isEqualTo("<strike>Eski</strike> <del>narx</del>");
    }

    @Test
    void toTelegramHtml_htmlFormat_convertsHeadingToBold() {
        String result = courseReaderService.toTelegramHtml("HTML", "<h2>Sarlavha</h2><p>Matn</p>");

        assertThat(result).isEqualTo("<b>Sarlavha</b>\nMatn");
    }

    // ===== Uzun matn bir nechta xabarga bo'linadi =====

    @Test
    void openSection_veryLongContent_splitsIntoMultipleMessages() {
        User user = student();
        String longText = "So'z ".repeat(2000); // taxminan 10000 belgi
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Uzun mavzu").orderIndex(1).type("TEXT")
                .textContent(longText).textContentFormat("PLAIN").completed(true).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        List<SendMessage> messages = courseReaderService.openSection(user, 5L, 10L);

        assertThat(messages.size()).isGreaterThan(1);
        // Faqat oxirgi xabarda navigatsiya tugmalari bo'lishi kerak.
        for (int i = 0; i < messages.size() - 1; i++) {
            assertThat(messages.get(i).getReplyMarkup()).isNull();
        }
        assertThat(messages.get(messages.size() - 1).getReplyMarkup()).isNotNull();
    }

    // ===== requestSubscription ("📩 Obunaga so'rov yuborish" — botning o'zidan) =====

    @Test
    void requestSubscription_success_showsConfirmation() {
        User user = student();

        SendMessage msg = courseReaderService.requestSubscription(user, 5L);

        org.mockito.Mockito.verify(courseSubscriptionService).requestSubscription(5L, user);
        assertThat(msg.getText()).contains("So'rovingiz yuborildi");
    }

    @Test
    void requestSubscription_alreadySubscribedOrPending_showsErrorMessage() {
        User user = student();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("❌Siz allaqachon shu kursga obuna bo'lgansiz"))
                .when(courseSubscriptionService).requestSubscription(5L, user);

        SendMessage msg = courseReaderService.requestSubscription(user, 5L);

        assertThat(msg.getText()).contains("allaqachon shu kursga obuna bo'lgansiz");
    }

    // ===== payWithClick ("💳 Click orqali to'lash" — botning o'zidan) =====

    @Test
    void payWithClick_success_showsWebAppPayButton() {
        User user = student();
        PaymentOrder order = PaymentOrder.builder().id(30L).amount(new java.math.BigDecimal("50000"))
                .durationMonths(1).status(PaymentOrderStatus.CREATED).courseId(5L).build();
        when(paymentOrderService.createCourseOrder(user, 5L, 1)).thenReturn(order);
        when(clickService.buildPayUrl(order, "/courses/5")).thenReturn("https://my.click.uz/services/pay?x=1");

        SendMessage msg = courseReaderService.payWithClick(user, 5L);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) msg.getReplyMarkup();
        assertThat(markup.getKeyboard().get(0).get(0).getWebApp().getUrl())
                .isEqualTo("https://my.click.uz/services/pay?x=1");
    }

    @Test
    void payWithClick_orderCreationFails_showsErrorMessage() {
        User user = student();
        when(paymentOrderService.createCourseOrder(user, 5L, 1))
                .thenThrow(new IllegalStateException("❌Kurs narxi hali belgilanmagan — OWNER bilan bog'laning"));

        SendMessage msg = courseReaderService.payWithClick(user, 5L);

        assertThat(msg.getText()).contains("narxi hali belgilanmagan");
    }
}
