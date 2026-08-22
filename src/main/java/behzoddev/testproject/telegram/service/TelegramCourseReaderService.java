package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSectionContentDto;
import behzoddev.testproject.dto.course.CourseSectionSummaryDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

// Botda "📚 Kurslar" — kurslarni to'g'ridan-to'g'ri Telegram ichida o'qish
// (saytga o'tishga hojatsiz): kurslar ro'yxati -> mavzular ro'yxati
// (sahifalab) -> mavzu matni. Faqat OBUNA BOR yoki BEPUL (free) yoki
// canManage kurslar to'liq ochiladi — CourseService.isSubscribed() shu
// mantiqni allaqachon hisoblaydi (free kurs uchun har doim true), shuning
// uchun bu servis saytdagi bilan bir xil ruxsat qoidalariga amal qiladi.
@Service
@RequiredArgsConstructor
public class TelegramCourseReaderService {

    private static final int SECTIONS_PER_PAGE = 8;
    // Telegram xabar chegarasi 4096 belgi — ehtiyot uchun pastroq chegara
    // bilan bo'laklaymiz (HTML teglar ham hisobga kirgani uchun).
    private static final int MAX_MESSAGE_LENGTH = 3500;

    private final CourseService courseService;
    private final TelegramAutoLoginService autoLoginService;

    /* ================= 1. Kurslar ro'yxati ================= */

    // Inline tugma matni Telegram tomonidan HAR DOIM markazlashtirilgan va
    // bitta qatorga sig'diriladi (uzun bo'lsa, Telegram o'zi "..." bilan
    // kesib qo'yadi) — bu bot API orqali o'zgartirib bo'lmaydigan, mijoz
    // ilovasi (Android/iOS/Desktop) darajasidagi cheklov. Shuning uchun
    // to'liq nom oddiy xabar MATNI sifatida (chapga tekislangan, o'zi
    // kerakli qatorlarga bo'linadigan) ko'rsatiladi, tugmalar esa faqat
    // kichik raqam/belgi bilan tanlash uchun ishlatiladi.
    public SendMessage showCourseList(User user) {
        List<CourseDto> courses = courseService.listCatalog(user);

        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());

        if (courses.isEmpty()) {
            msg.setText("📚 Hozircha kurslar mavjud emas.");
            return msg;
        }

        StringBuilder sb = new StringBuilder("📚 <b>Mavjud kurslar</b>\n\nO'qish uchun raqamini tanlang:\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int i = 1;
        for (CourseDto c : courses) {
            String icon = !c.published() ? "📝" : c.free() ? "🆓" : c.subscribed() ? "✅" : "🔒";
            sb.append(icon).append(" ").append(i).append(". ").append(escape(c.title())).append("\n");
            rows.add(List.of(button(icon + " " + i, "course_open_" + c.id())));
            i++;
        }

        msg.setText(sb.toString());
        msg.setParseMode("HTML");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    /* ================= 2. Kursni ochish ================= */

    public SendMessage openCourse(User user, Long courseId) {
        CourseDetailDto course;
        try {
            course = courseService.getDetail(courseId, user);
        } catch (NoSuchElementException e) {
            return simpleMessage(user, "❌ Kurs topilmadi.");
        }

        if (!course.subscribed()) {
            return accessDeniedMessage(user, course);
        }

        return renderSectionsPage(user, course, 0);
    }

    private SendMessage accessDeniedMessage(User user, CourseDetailDto course) {
        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());

        String url = autoLoginService.buildLoginUrl(user, "/courses/" + course.id());
        msg.setText("🔒 <b>" + escape(course.title()) + "</b>\n\n" +
                "Bu kursni botda o'qish uchun obuna kerak. Saytda batafsil ko'rib, " +
                "obuna so'rovi yuborishingiz mumkin: " + url);
        msg.setParseMode("HTML");
        // Telegram'ning preview-fetcheri xabar yuborilishi bilan havolani
        // o'zi ochib, bitta martalik autologin tokenini ishlatib qo'ymasligi uchun.
        msg.setDisableWebPagePreview(true);
        msg.setReplyMarkup(backToCoursesMarkup());
        return msg;
    }

    /* ================= 3. Mavzular ro'yxati (sahifalab) ================= */

    public SendMessage showSectionsPage(User user, Long courseId, int page) {
        CourseDetailDto course;
        try {
            course = courseService.getDetail(courseId, user);
        } catch (NoSuchElementException e) {
            return simpleMessage(user, "❌ Kurs topilmadi.");
        }

        if (!course.subscribed()) {
            return accessDeniedMessage(user, course);
        }

        return renderSectionsPage(user, course, page);
    }

    private SendMessage renderSectionsPage(User user, CourseDetailDto course, int page) {
        List<CourseSectionSummaryDto> sections = course.sections();

        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());
        msg.setParseMode("HTML");

        if (sections.isEmpty()) {
            msg.setText("📋 <b>" + escape(course.title()) + "</b>\n\nHali bo'lim yo'q.");
            msg.setReplyMarkup(backToCoursesMarkup());
            return msg;
        }

        int totalPages = (int) Math.ceil(sections.size() / (double) SECTIONS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * SECTIONS_PER_PAGE;
        int to = Math.min(from + SECTIONS_PER_PAGE, sections.size());

        // To'liq mavzu nomi — chapga tekislangan, o'zi kerakli qatorlarga
        // bo'linadigan oddiy xabar matni sifatida (inline tugma matni
        // Telegram tomonidan markazlashtiriladi va bitta qatorga
        // kesiladi — uzun nomlar to'liq ko'rinmasdi). Tugmalar endi faqat
        // raqam bilan tanlash uchun.
        StringBuilder sb = new StringBuilder("📋 <b>" + escape(course.title()) + "</b>\n\nMavzuni tanlang (" +
                (safePage + 1) + "/" + totalPages + "-sahifa):\n\n");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (CourseSectionSummaryDto s : sections.subList(from, to)) {
            String icon = s.completed() ? "✅" : s.locked() ? "🔒" : "▫️";
            sb.append(icon).append(" ").append(s.orderIndex()).append(". ").append(escape(s.title())).append("\n");
            rows.add(List.of(button(icon + " " + s.orderIndex(),
                    "course_sec_" + course.id() + "_" + s.id())));
        }

        msg.setText(sb.toString());

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (safePage > 0) {
            navRow.add(button("◀️", "course_secs_" + course.id() + "_" + (safePage - 1)));
        }
        if (safePage < totalPages - 1) {
            navRow.add(button("▶️", "course_secs_" + course.id() + "_" + (safePage + 1)));
        }
        if (!navRow.isEmpty()) rows.add(navRow);
        rows.add(List.of(button("🔙 Kurslar ro'yxati", "course_list")));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    /* ================= 4. Mavzu matnini ko'rsatish ================= */

    public List<SendMessage> openSection(User user, Long courseId, Long sectionId) {
        CourseSectionContentDto section;
        try {
            section = courseService.getSectionContent(courseId, sectionId, user);
        } catch (AccessDeniedException e) {
            return List.of(simpleMessage(user, "⛔ " + e.getMessage()));
        } catch (NoSuchElementException e) {
            return List.of(simpleMessage(user, "❌ Bo'lim topilmadi."));
        }

        // TEXT bo'lim — saytdagi kabi ochilgan zahoti "tugatilgan" deb
        // belgilanadi (CourseSectionView.js'dagi markCompleted() bilan bir xil).
        if ("TEXT".equals(section.type()) && !section.completed()) {
            courseService.markSectionCompleted(courseId, sectionId, user);
            section = courseService.getSectionContent(courseId, sectionId, user);
        }

        return buildSectionMessages(user, section);
    }

    public List<SendMessage> completeAndAdvance(User user, Long courseId, Long sectionId) {
        try {
            courseService.markSectionCompleted(courseId, sectionId, user);
        } catch (AccessDeniedException e) {
            return List.of(simpleMessage(user, "⛔ " + e.getMessage()));
        }
        return openSection(user, courseId, sectionId);
    }

    private List<SendMessage> buildSectionMessages(User user, CourseSectionContentDto section) {
        String body = renderSectionBody(section);
        List<String> chunks = splitIntoChunks(body);

        List<SendMessage> messages = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            SendMessage msg = new SendMessage();
            msg.setChatId(user.getTelegramId().toString());
            msg.setText(chunks.get(i));
            msg.setParseMode("HTML");
            msg.setDisableWebPagePreview(true);

            if (i == chunks.size() - 1) {
                msg.setReplyMarkup(sectionNavMarkup(section));
            }
            messages.add(msg);
        }
        return messages;
    }

    private String renderSectionBody(CourseSectionContentDto section) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 <b>").append(escape(section.title())).append("</b>\n\n");

        if (section.textContent() != null && !section.textContent().isBlank()) {
            sb.append(toTelegramHtml(section.textContentFormat(), section.textContent()));
        }

        if (section.videoUrl() != null && !section.videoUrl().isBlank()) {
            sb.append("\n\n🎬 Video: ").append(escape(videoLink(section)));
        }

        return sb.toString();
    }

    private String videoLink(CourseSectionContentDto section) {
        if ("YOUTUBE".equals(section.videoSourceType())) {
            return "https://youtu.be/" + section.videoUrl();
        }
        // UPLOAD/EXTERNAL — nisbiy yo'l bo'lishi mumkin (sayt ichidagi
        // "/uploads/..." fayl), to'liq havolaga aylantiramiz.
        return section.videoUrl().startsWith("http")
                ? section.videoUrl()
                : "https://study-grow.uz" + section.videoUrl();
    }

    private InlineKeyboardMarkup sectionNavMarkup(CourseSectionContentDto section) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // VIDEO/MIXED — saytdagidek qo'lda "tugatdim" bosish kerak (TEXT
        // kabi ochilgan zahoti avtomatik emas), aks holda "Keyingi" tugmasi
        // hech qachon ochilmay qolardi.
        if (!section.completed() && !"TEXT".equals(section.type())) {
            rows.add(List.of(button("✅ Tugatdim",
                    "course_complete_" + section.courseId() + "_" + section.id())));
        }

        if (section.linkedTopicId() != null) {
            InlineKeyboardButton testBtn = new InlineKeyboardButton();
            testBtn.setText("🎯 Mavzuga oid testlarni yechish");
            testBtn.setUrl("https://study-grow.uz/testConfigPage?scienceId=" + section.linkedScienceId()
                    + "&topicId=" + section.linkedTopicId());
            rows.add(List.of(testBtn));
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (section.prevSectionId() != null) {
            navRow.add(button("⬅️ Oldingi", "course_sec_" + section.courseId() + "_" + section.prevSectionId()));
        }
        if (section.nextSectionId() != null && section.nextUnlocked()) {
            navRow.add(button("Keyingi ➡️", "course_sec_" + section.courseId() + "_" + section.nextSectionId()));
        }
        if (!navRow.isEmpty()) rows.add(navRow);

        rows.add(List.of(button("📋 Mavzular ro'yxati", "course_secs_" + section.courseId() + "_0")));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    /* ================= HTML -> Telegram HTML ================= */

    // Telegram Bot API faqat cheklangan teglar to'plamini qo'llab-quvvatlaydi
    // (b, strong, i, em, u, s, a, code, pre). Kurs matni esa contenteditable
    // (p, div, br, ul/ol/li, h1-h6, table) yoki eski xom PLAIN matn bo'lishi
    // mumkin — shularni Telegram tushunadigan ko'rinishga aylantiramiz.
    // Qo'llab-quvvatlanmagan teg yuborilsa Telegram butun xabarni "can't
    // parse entities" xatosi bilan rad etadi, shuning uchun oxirida
    // noma'lum qolgan har qanday teg olib tashlanadi (whitelist-strip).
    private static final Pattern BLOCK_CLOSE = Pattern.compile("(?i)</p>|</div>|<br\\s*/?>");
    private static final Pattern BLOCK_OPEN = Pattern.compile("(?i)<p[^>]*>|<div[^>]*>");
    private static final Pattern HEADING_OPEN = Pattern.compile("(?i)<h[1-6][^>]*>");
    private static final Pattern HEADING_CLOSE = Pattern.compile("(?i)</h[1-6]>");
    private static final Pattern LIST_ITEM_OPEN = Pattern.compile("(?i)<li[^>]*>");
    private static final Pattern LIST_ITEM_CLOSE = Pattern.compile("(?i)</li>");
    private static final Pattern LIST_WRAP = Pattern.compile("(?i)</?(ul|ol)[^>]*>");
    private static final Pattern TABLE_CELL_CLOSE = Pattern.compile("(?i)</t[dh]>");
    private static final Pattern TABLE_ROW_CLOSE = Pattern.compile("(?i)</tr>");
    private static final Pattern TABLE_STRUCTURE = Pattern.compile("(?i)</?(table|thead|tbody|tr|td|th)[^>]*>");
    private static final Pattern UNSUPPORTED_TAG = Pattern.compile(
            "(?i)</?(?!b\\b|strong\\b|i\\b|em\\b|u\\b|s\\b|a\\b|code\\b|pre\\b)[a-z][^>]*>");
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\n{3,}");

    String toTelegramHtml(String textContentFormat, String rawContent) {
        if (rawContent == null) return "";

        if (!"HTML".equals(textContentFormat)) {
            // PLAIN — xom matn (hali WYSIWYG'gacha yozilgan eski bo'limlar),
            // hech qanday HTML tegi yo'q — Telegram HTML rejimida buzilib
            // ketmasligi uchun xavfsiz escape qilinadi. Telegram bare
            // http(s) havolalarni o'zi ham avtomatik bosiladigan qiladi.
            return escape(rawContent);
        }

        String html = rawContent;
        html = HEADING_OPEN.matcher(html).replaceAll("<b>");
        html = HEADING_CLOSE.matcher(html).replaceAll("</b>\n");
        html = LIST_ITEM_OPEN.matcher(html).replaceAll("• ");
        html = LIST_ITEM_CLOSE.matcher(html).replaceAll("\n");
        html = LIST_WRAP.matcher(html).replaceAll("");
        html = TABLE_CELL_CLOSE.matcher(html).replaceAll(" | ");
        html = TABLE_ROW_CLOSE.matcher(html).replaceAll("\n");
        html = TABLE_STRUCTURE.matcher(html).replaceAll("");
        html = BLOCK_CLOSE.matcher(html).replaceAll("\n");
        html = BLOCK_OPEN.matcher(html).replaceAll("");
        html = UNSUPPORTED_TAG.matcher(html).replaceAll("");
        html = MULTI_BLANK_LINES.matcher(html).replaceAll("\n\n");

        return html.trim();
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String remaining = text;

        while (remaining.length() > MAX_MESSAGE_LENGTH) {
            int splitAt = remaining.lastIndexOf("\n\n", MAX_MESSAGE_LENGTH);
            if (splitAt < 500) splitAt = remaining.lastIndexOf('\n', MAX_MESSAGE_LENGTH);
            if (splitAt < 500) splitAt = MAX_MESSAGE_LENGTH;

            chunks.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt).trim();
        }
        if (!remaining.isBlank() || chunks.isEmpty()) {
            chunks.add(remaining);
        }
        return chunks;
    }

    /* ================= Yordamchilar ================= */

    private SendMessage simpleMessage(User user, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());
        msg.setText(text);
        msg.setReplyMarkup(backToCoursesMarkup());
        return msg;
    }

    private InlineKeyboardMarkup backToCoursesMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(button("🔙 Kurslar ro'yxati", "course_list"))));
        return markup;
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
