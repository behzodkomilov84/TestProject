package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSectionContentDto;
import behzoddev.testproject.dto.course.CourseSectionSummaryDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

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
    private TelegramAutoLoginService autoLoginService;

    @InjectMocks
    private TelegramCourseReaderService courseReaderService;

    private User student() {
        Role role = Role.builder().id(1L).roleName("ROLE_USER").build();
        return User.builder().id(1L).username("student").telegramId(100L)
                .roles(new HashSet<>(Set.of(role))).build();
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(autoLoginService.buildLoginUrl(any(), any()))
                .thenReturn("https://study-grow.uz/telegram-auto-login?token=stub");
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

    // ===== openCourse (ruxsat tekshiruvi) =====

    @Test
    void openCourse_notSubscribedAndNotFree_showsAccessDeniedWithLoginLink() {
        User user = student();
        CourseDetailDto course = CourseDetailDto.builder().id(5L).title("Kimyo").published(true)
                .free(false).subscribed(false).canManage(false).sections(List.of()).build();
        when(courseService.getDetail(5L, user)).thenReturn(course);

        SendMessage msg = courseReaderService.openCourse(user, 5L);

        assertThat(msg.getText()).contains("obuna kerak").contains("study-grow.uz");
        org.mockito.Mockito.verify(autoLoginService).buildLoginUrl(user, "/courses/5");
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
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) page0.getReplyMarkup();
        List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
        assertThat(rows).hasSize(8 + 1 + 1); // 8 ta mavzu + navigatsiya qatori + "orqaga"
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
    void openSection_linkedTopic_showsTestUrlButton() {
        User user = student();
        CourseSectionContentDto section = CourseSectionContentDto.builder()
                .id(10L).courseId(5L).title("Mavzu").orderIndex(1).type("TEXT")
                .textContent("Matn").textContentFormat("PLAIN").completed(true)
                .linkedScienceId(3L).linkedTopicId(7L).build();
        when(courseService.getSectionContent(5L, 10L, user)).thenReturn(section);

        List<SendMessage> messages = courseReaderService.openSection(user, 5L, 10L);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) messages.get(0).getReplyMarkup();
        boolean hasTestButton = markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(b -> b.getUrl() != null && b.getUrl().contains("scienceId=3") && b.getUrl().contains("topicId=7"));
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
}
