package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSectionContentDto;
import behzoddev.testproject.dto.course.CourseSectionSummaryDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseService;
import behzoddev.testproject.service.CourseSubscriptionService;
import behzoddev.testproject.service.payment.ClickService;
import behzoddev.testproject.service.payment.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Botda "📚 Kurslar" — kurslarni to'g'ridan-to'g'ri Telegram ichida o'qish
// (saytga o'tishga hojatsiz): kurslar ro'yxati -> darslar ro'yxati
// (sahifalab) -> dars matni. Faqat OBUNA BOR yoki BEPUL (free) yoki
// canManage kurslar to'liq ochiladi — CourseService.isSubscribed() shu
// mantiqni allaqachon hisoblaydi (free kurs uchun har doim true), shuning
// uchun bu servis saytdagi bilan bir xil ruxsat qoidalariga amal qiladi.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCourseReaderService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private static final int SECTIONS_PER_PAGE = 8;
    // Kurs/dars tanlash tugmalari endi yonma-yon (avval har biri alohida
    // qatorda, ustma-ust turardi).
    private static final int BUTTONS_PER_ROW = 4;
    // Telegram xabar chegarasi 4096 belgi — ehtiyot uchun pastroq chegara
    // bilan bo'laklaymiz (HTML teglar ham hisobga kirgani uchun).
    private static final int MAX_MESSAGE_LENGTH = 3500;
    // Ro'yxat bandlari orasidagi ajratuvchi chiziq — nomlar uzun bo'lganda
    // (ayniqsa ko'p qatorli) bandlar bir-biriga "yopishib" ketmasligi uchun
    // (foydalanuvchi so'rovi bo'yicha).
    private static final String LIST_SEPARATOR = "➖➖➖➖➖➖➖➖➖➖\n";

    private final CourseService courseService;
    private final PaymentOrderService paymentOrderService;
    private final ClickService clickService;
    private final CourseSubscriptionService courseSubscriptionService;

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
        List<InlineKeyboardButton> buttons = new ArrayList<>();

        int i = 1;
        for (CourseDto c : courses) {
            if (i > 1) sb.append(LIST_SEPARATOR);
            String icon = !c.published() ? "📝" : c.free() ? "🆓" : c.subscribed() ? "✅" : "🔒";
            // Narxi belgilangan bo'lsa — foydalanuvchi obunaga so'rov
            // yuborishdan oldin qancha to'lashini ko'rib turishi uchun
            // (saytdagi katalog belgisi bilan bir xil g'oya).
            String priceText = (!c.free() && !c.subscribed() && c.price() != null)
                    ? " — " + formatPrice(c.price()) + " so'm"
                    : "";
            sb.append(icon).append(" ").append(i).append(". ").append(escape(c.title())).append(priceText).append("\n");
            buttons.add(button(icon + " " + i, "course_open_" + c.id()));
            i++;
        }

        msg.setText(sb.toString());
        msg.setParseMode("HTML");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(chunkIntoRows(buttons));
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

        String priceText = course.price() != null ? " Narxi: " + formatPrice(course.price()) + " so'm." : "";
        StringBuilder text = new StringBuilder("🔒 <b>" + escape(course.title()) + "</b>\n\n" +
                "Bu kursni botda o'qish uchun obuna kerak." + priceText);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Saytdagi "💳 Click orqali to'lash" bilan bir xil — narx belgilangan
        // va Click ulangan bo'lsa, to'lov muvaffaqiyatli bo'lishi bilanoq
        // (OWNER kutmasdan) kursga kirish avtomatik ochiladi.
        if (clickService.isEnabled() && course.price() != null) {
            InlineKeyboardButton payBtn = new InlineKeyboardButton();
            payBtn.setText("💳 Click orqali to'lash");
            payBtn.setCallbackData("course_pay_" + course.id());
            rows.add(List.of(payBtn));
        }

        // Obuna so'rovi endi saytga o'tkazuvchi havola emas — to'g'ridan-
        // to'g'ri botning o'zida yuboriladi (CourseSubscriptionService.
        // requestSubscription), OWNER buni "Kursga obuna berish"
        // sahifasida ko'rib, qo'lda tasdiqlaydi.
        if (course.requestPending()) {
            text.append("\n\n⏳ Obunaga so'rovingiz allaqachon yuborilgan — administrator (OWNER) javobini kuting.");
        } else {
            InlineKeyboardButton requestBtn = new InlineKeyboardButton();
            requestBtn.setText("📩 Obunaga so'rov yuborish");
            requestBtn.setCallbackData("course_request_" + course.id());
            rows.add(List.of(requestBtn));
        }

        rows.add(List.of(button("🔙 Kurslar ro'yxati", "course_list")));

        msg.setText(text.toString());
        msg.setParseMode("HTML");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    // "📩 Obunaga so'rov yuborish" tugmasi bosilganda — saytdagi
    // requestSubscription() bilan bir xil (CourseSubscriptionService orqali),
    // faqat botning o'zida, saytga o'tishga hojatsiz.
    public SendMessage requestSubscription(User user, Long courseId) {
        try {
            courseSubscriptionService.requestSubscription(courseId, user);
            return simpleMessage(user,
                    "✅ So'rovingiz yuborildi. Administrator (OWNER) ko'rib chiqib, obunani tasdiqlaydi.");
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return simpleMessage(user, "❌ " + e.getMessage());
        }
    }

    // "💳 Click orqali to'lash" tugmasi bosilganda — TelegramMenuService.
    // createClickPaymentLink bilan bir xil g'oya: checkout link Telegram
    // "Web App" tugmasi sifatida beriladi, botdan chiqmasdan (o'zining
    // ichki brauzerida) to'lov yakunlanadi.
    public SendMessage payWithClick(User user, Long courseId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(user.getTelegramId().toString());

        try {
            PaymentOrder order = paymentOrderService.createCourseOrder(user, courseId, 1);
            String checkoutUrl = clickService.buildPayUrl(order, "/courses/" + courseId);

            msg.setText("💳 To'lovni yakunlash uchun quyidagi tugmani bosing " +
                    "(botdan chiqmasdan, Telegram ichida ochiladi):");

            InlineKeyboardButton payBtn = new InlineKeyboardButton();
            payBtn.setText("💳 Click orqali to'lash");
            payBtn.setWebApp(WebAppInfo.builder().url(checkoutUrl).build());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(payBtn)));
            msg.setReplyMarkup(markup);
        } catch (IllegalArgumentException | IllegalStateException e) {
            msg.setText("❌ " + e.getMessage());
            msg.setReplyMarkup(backToCoursesMarkup());
        }

        return msg;
    }

    /* ================= 3. Darslar ro'yxati (sahifalab) ================= */

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
            msg.setText("📋 <b>" + escape(course.title()) + "</b>\n\nHali dars yo'q.");
            msg.setReplyMarkup(backToCoursesMarkup());
            return msg;
        }

        int totalPages = (int) Math.ceil(sections.size() / (double) SECTIONS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * SECTIONS_PER_PAGE;
        int to = Math.min(from + SECTIONS_PER_PAGE, sections.size());

        // To'liq dars nomi — chapga tekislangan, o'zi kerakli qatorlarga
        // bo'linadigan oddiy xabar matni sifatida (inline tugma matni
        // Telegram tomonidan markazlashtiriladi va bitta qatorga
        // kesiladi — uzun nomlar to'liq ko'rinmasdi). Tugmalar endi faqat
        // raqam bilan tanlash uchun.
        StringBuilder sb = new StringBuilder("📋 <b>" + escape(course.title()) + "</b>\n\nDarsni tanlang (" +
                (safePage + 1) + "/" + totalPages + "-sahifa):\n\n");

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        boolean first = true;
        for (CourseSectionSummaryDto s : sections.subList(from, to)) {
            if (!first) sb.append(LIST_SEPARATOR);
            first = false;
            String icon = s.completed() ? "✅" : s.locked() ? "🔒" : "▫️";
            sb.append(icon).append(" ").append(s.orderIndex()).append(". ").append(escape(s.title())).append("\n");
            buttons.add(button(icon + " " + s.orderIndex(), "course_sec_" + course.id() + "_" + s.id()));
        }

        msg.setText(sb.toString());

        // Tugmalar yonma-yon (4 tadan bir qatorda) — avval har biri alohida
        // qatorda ustma-ust turardi.
        List<List<InlineKeyboardButton>> rows = new ArrayList<>(chunkIntoRows(buttons));

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

    /* ================= 4. Dars matnini ko'rsatish ================= */

    public List<SendMessage> openSection(User user, Long courseId, Long sectionId) {
        CourseSectionContentDto section;
        try {
            section = courseService.getSectionContent(courseId, sectionId, user);
        } catch (AccessDeniedException e) {
            return List.of(simpleMessage(user, "⛔ " + e.getMessage()));
        } catch (NoSuchElementException e) {
            return List.of(simpleMessage(user, "❌ Dars topilmadi."));
        }

        // TEXT dars — saytdagi kabi ochilgan zahoti "tugatilgan" deb
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

    // "📥 Kitobni to'liq yuklab olish" kabi PDF/DOCX/ZIP havolalari dars
    // matnida bo'lsa — ularni oddiy matn linki sifatida qoldirish o'rniga
    // (bosilganda tashqi brauzerga o'tkazadi), fayl to'g'ridan-to'g'ri
    // botning o'zida (hujjat sifatida) yuboriladi. Telegram serveri
    // havoladan faylni o'zi yuklab oladi — bizga faylni qayta yuklash
    // shart emas.
    private static final Pattern FILE_LINK = Pattern.compile(
            "(?i)https?://[^\\s\"'<>]+\\.(?:pdf|docx?|zip)");

    public List<SendDocument> documentsForSection(User user, Long courseId, Long sectionId) {
        CourseSectionContentDto section;
        try {
            section = courseService.getSectionContent(courseId, sectionId, user);
        } catch (Exception e) {
            return List.of();
        }

        List<String> links = extractFileLinks(section.textContent());
        List<SendDocument> documents = new ArrayList<>();
        for (String link : links) {
            SendDocument doc = buildDocument(user, link);
            if (doc != null) documents.add(doc);
        }
        return documents;
    }

    // Havoladan Telegram serverining o'zi yuklab olishiga ishonib
    // bo'lmaydi — production'da "[400] Bad Request: failed to get HTTP
    // URL content" xatosi bilan muvaffaqiyatsiz tugadi (Telegram fetcher
    // ba'zan saytga yeta olmaydi/ SSL yoki User-Agent bilan bog'liq
    // muammolar). Fayl saytning o'z /uploads/ papkasida (WebConfig —
    // /uploads/** -> {app.upload.dir}/**) va bot xuddi shu serverda
    // ishlaydi, shuning uchun faylni diskdan to'g'ridan-to'g'ri o'qib,
    // multipart sifatida Telegram'ga yuklaymiz — tashqi HTTP so'rovga
    // umuman hojat yo'q, bir yo'la ancha ishonchli.
    private SendDocument buildDocument(User user, String link) {
        int idx = link.indexOf("/uploads/");
        if (idx < 0) {
            log.warn("Bot: kurs faylining havolasi /uploads/ ichida emas, o'tkazib yuborildi: {}", link);
            return null;
        }

        String relativePath = link.substring(idx + "/uploads/".length());
        Path baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(relativePath).normalize();

        // Path traversal'dan himoya — fayl haqiqatan ham upload papkasi ichida ekanligini tekshiramiz.
        if (!filePath.startsWith(baseDir) || !Files.isRegularFile(filePath)) {
            log.warn("Bot: kurs faylini diskdan topib bo'lmadi: {}", filePath);
            return null;
        }

        SendDocument doc = new SendDocument();
        doc.setChatId(user.getTelegramId().toString());
        doc.setDocument(new InputFile(filePath.toFile(), filePath.getFileName().toString()));
        return doc;
    }

    private List<String> extractFileLinks(String rawContent) {
        if (rawContent == null) return List.of();

        Set<String> links = new LinkedHashSet<>();
        Matcher m = FILE_LINK.matcher(rawContent);
        while (m.find()) {
            links.add(m.group());
        }
        return new ArrayList<>(links);
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

        // Saytga o'tkazuvchi havola o'rniga — to'g'ridan-to'g'ri botning
        // o'zida (TelegramPracticeTestService.startForTopic orqali) shu
        // dars bo'yicha testni yechish imkoniyati.
        if (section.linkedTopicId() != null) {
            rows.add(List.of(button("🎯 Darsga oid testlarni yechish", "course_test_" + section.linkedTopicId())));
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (section.prevSectionId() != null) {
            navRow.add(button("⬅️ Oldingi", "course_sec_" + section.courseId() + "_" + section.prevSectionId()));
        }
        if (section.nextSectionId() != null && section.nextUnlocked()) {
            navRow.add(button("Keyingi ➡️", "course_sec_" + section.courseId() + "_" + section.nextSectionId()));
        }
        if (!navRow.isEmpty()) rows.add(navRow);

        rows.add(List.of(button("📋 Darslar ro'yxati", "course_secs_" + section.courseId() + "_0")));

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
    // "strike"/"del" ham qo'shildi — kurs matni tahrirlagichidagi yangi
    // "S̶ (chizib o'tish)" tugmasi Chrome'da execCommand('strikeThrough')
    // orqali <strike> tegini chiqaradi (<s> emas); Telegram HTML rejimi
    // <s>/<strike>/<del> uchligini bab-baravar chizib o'tilgan matn
    // sifatida qo'llab-quvvatlaydi, shuning uchun ikkalasi ham saqlanadi.
    private static final Pattern UNSUPPORTED_TAG = Pattern.compile(
            "(?i)</?(?!b\\b|strong\\b|i\\b|em\\b|u\\b|s\\b|strike\\b|del\\b|a\\b|code\\b|pre\\b)[a-z][^>]*>");
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\n{3,}");

    String toTelegramHtml(String textContentFormat, String rawContent) {
        if (rawContent == null) return "";

        if (!"HTML".equals(textContentFormat)) {
            // PLAIN — xom matn (hali WYSIWYG'gacha yozilgan eski darslar),
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

    // Tekis tugmalar ro'yxatini BUTTONS_PER_ROW tadan qatorlarga bo'ladi
    // (kurslar/darslar ro'yxatida yonma-yon ko'rinishi uchun).
    private List<List<InlineKeyboardButton>> chunkIntoRows(List<InlineKeyboardButton> buttons) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += BUTTONS_PER_ROW) {
            rows.add(new ArrayList<>(buttons.subList(i, Math.min(i + BUTTONS_PER_ROW, buttons.size()))));
        }
        return rows;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // "150000" -> "150 000" — minglik ajratkichli, saytdagi formatPrice()
    // bilan bir xil ko'rinish. Locale.US aniq ko'rsatilgan — aks holda
    // "%,d" JVM standart lokaliga qarab vergul o'rniga boshqa (masalan
    // uzilmaydigan bo'shliq) belgi ishlatishi mumkin edi, va keyingi
    // replace(",", " ") hech narsa qilmay qolardi.
    private String formatPrice(java.math.BigDecimal price) {
        return String.format(java.util.Locale.US, "%,d", price.longValue()).replace(",", " ");
    }
}
