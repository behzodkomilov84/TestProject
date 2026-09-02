package behzoddev.testproject.service;

import behzoddev.testproject.entity.enums.CourseSectionContentFormat;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Kurs mavzusi matnini (CourseSection#textContent — PLAIN yoki HTML,
// rich-text-editor/.docx import orqali saqlangan) .docx hujjatga
// "imkon qadar maksimal" formatlash bilan o'giradi: qalin/kursiv/
// tagiga chizilgan/chizib o'chirilgan, giperssilkalar, ro'yxatlar
// (bullet/raqamli), jadvallar, rasmlar (o'lchami moslab) va PPT
// slaydlar (HAR BIRI ketma-ket, tartib raqami bilan — courseDetail.js#
// insertPptSlideshowHtml faqat 1-slaydni <img>da, qolganlarini
// data-slides JSON'ida saqlaydi).
//
// Oddiy statik utility (DI shart emas) — CourseWordExportService shu
// yerdagi bitta kirish nuqtasini (convert) chaqiradi.
final class HtmlToDocxConverter {

    private HtmlToDocxConverter() {
    }

    private static final Pattern WIDTH_PX_PATTERN = Pattern.compile("width\\s*:\\s*(\\d+)px");

    private record InlineStyle(boolean bold, boolean italic, boolean underline, boolean strike, String linkUrl) {
        static final InlineStyle PLAIN = new InlineStyle(false, false, false, false, null);

        InlineStyle withBold() {
            return new InlineStyle(true, italic, underline, strike, linkUrl);
        }

        InlineStyle withItalic() {
            return new InlineStyle(bold, true, underline, strike, linkUrl);
        }

        InlineStyle withUnderline() {
            return new InlineStyle(bold, italic, true, strike, linkUrl);
        }

        InlineStyle withStrike() {
            return new InlineStyle(bold, italic, underline, true, linkUrl);
        }

        InlineStyle withLink(String url) {
            return new InlineStyle(bold, italic, underline, strike, url);
        }
    }

    static void convert(XWPFDocument doc, String content, CourseSectionContentFormat format, String uploadDir) {
        if (content == null || content.isBlank()) {
            return;
        }

        if (format != CourseSectionContentFormat.HTML) {
            for (String line : content.split("\\r?\\n")) {
                XWPFParagraph p = doc.createParagraph();
                if (!line.isBlank()) {
                    XWPFRun r = p.createRun();
                    r.setText(line);
                    r.setFontSize(11);
                }
            }
            return;
        }

        Document jsoupDoc = Jsoup.parse(content, "", Parser.htmlParser());
        for (Node child : List.copyOf(jsoupDoc.body().childNodes())) {
            renderBlock(doc, child, uploadDir);
        }
    }

    // ===================== Blok darajasi =====================

    private static void renderBlock(XWPFDocument doc, Node node, String uploadDir) {
        if (node instanceof TextNode tn) {
            if (!tn.isBlank()) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(tn.text());
                r.setFontSize(11);
            }
            return;
        }
        if (!(node instanceof Element el)) {
            return;
        }

        switch (el.tagName().toLowerCase()) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingBefore(200);
                p.setSpacingAfter(100);
                int level = Integer.parseInt(el.tagName().substring(1));
                int size = switch (level) {
                    case 1 -> 20;
                    case 2 -> 18;
                    case 3 -> 16;
                    case 4 -> 14;
                    case 5 -> 13;
                    default -> 12;
                };
                XWPFParagraph result = renderInlineChildren(doc, p, el, uploadDir, InlineStyle.PLAIN.withBold());
                for (XWPFRun run : result.getRuns()) {
                    run.setFontSize(size);
                }
            }
            case "ul", "ol" -> renderList(doc, el, uploadDir, 0, "ol".equals(el.tagName().toLowerCase()));
            case "table" -> renderTable(doc, el, uploadDir);
            case "br" -> {
                // Alohida, matndan tashqarida turgan <br> — bo'sh qator.
                doc.createParagraph();
            }
            case "span" -> {
                if (isPptWrap(el)) {
                    renderPptSlides(doc, el, uploadDir);
                } else if (isImgWrap(el)) {
                    renderStandaloneImage(doc, el, uploadDir);
                } else {
                    XWPFParagraph p = doc.createParagraph();
                    renderInlineChildren(doc, p, el, uploadDir, InlineStyle.PLAIN);
                }
            }
            case "p", "div", "li" -> {
                if (isPptWrap(el)) {
                    renderPptSlides(doc, el, uploadDir);
                    return;
                }
                if (isImgWrap(el)) {
                    renderStandaloneImage(doc, el, uploadDir);
                    return;
                }
                XWPFParagraph p = doc.createParagraph();
                renderInlineChildren(doc, p, el, uploadDir, InlineStyle.PLAIN);
            }
            default -> {
                // Noma'lum konteyner (masalan <section>, <blockquote>, <body>) —
                // farzandlarini blok darajasida davom ettirib chizamiz.
                for (Node child : List.copyOf(el.childNodes())) {
                    renderBlock(doc, child, uploadDir);
                }
            }
        }
    }

    private static boolean isPptWrap(Element el) {
        return el.hasClass("rich-ppt-wrap");
    }

    private static boolean isImgWrap(Element el) {
        return el.hasClass("rich-img-wrap");
    }

    // "rich-ppt-wrap" — courseDetail.js#insertPptSlideshowHtml. Faqat
    // JORIY ko'rsatilayotgan slayd <img>da, QOLGAN barcha slaydlar
    // "data-slides" JSON atributida. Foydalanuvchi so'rovi bo'yicha —
    // HAR BIRINI ketma-ket, tartib raqami bilan joylashtiramiz.
    private static void renderPptSlides(XWPFDocument doc, Element wrap, String uploadDir) {
        String slidesJson = wrap.attr("data-slides");
        List<String> slides = parseJsonStringArray(slidesJson);
        if (slides.isEmpty()) {
            // JSON o'qilmasa — hech bo'lmasa ko'rinib turgan bitta <img>ni qo'shamiz.
            Element img = wrap.selectFirst("img");
            if (img != null) {
                slides = List.of(img.attr("src"));
            }
        }

        for (int i = 0; i < slides.size(); i++) {
            XWPFParagraph caption = doc.createParagraph();
            caption.setSpacingBefore(80);
            XWPFRun captionRun = caption.createRun();
            captionRun.setBold(true);
            captionRun.setFontSize(10);
            captionRun.setText((i + 1) + "-slayd:");

            XWPFParagraph imgPara = doc.createParagraph();
            imgPara.setSpacingAfter(100);
            XWPFRun imgRun = imgPara.createRun();
            DocxImageUtil.insertInlinePicture(imgRun, uploadDir, slides.get(i), null);
        }
    }

    // Oddiy JSON string massivini ("[\"/uploads/a.png\",\"/uploads/b.png\"]")
    // tashqi kutubxonasiz o'qiydi — Jackson/Gson shart emas, bu yerda
    // faqat qo'shtirnoqli qatorlar ro'yxati kerak.
    private static List<String> parseJsonStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        List<String> result = new java.util.ArrayList<>();
        while (m.find()) {
            result.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return result;
    }

    private static void renderStandaloneImage(XWPFDocument doc, Element wrap, String uploadDir) {
        Element img = wrap.selectFirst("img");
        if (img == null) {
            return;
        }
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(60);
        p.setSpacingAfter(100);
        XWPFRun run = p.createRun();
        DocxImageUtil.insertInlinePicture(run, uploadDir, img.attr("src"), extractWidthPx(img));
    }

    private static Integer extractWidthPx(Element img) {
        Matcher m = WIDTH_PX_PATTERN.matcher(img.attr("style"));
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // pastda null qaytadi
            }
        }
        return null;
    }

    private static void renderList(XWPFDocument doc, Element listEl, String uploadDir, int depth, boolean ordered) {
        int number = 1;
        for (Element li : listEl.children()) {
            if (!"li".equalsIgnoreCase(li.tagName())) {
                continue;
            }

            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(400 + depth * 300);
            String marker = ordered ? (number++ + ". ") : "• ";
            XWPFRun markerRun = p.createRun();
            markerRun.setText(marker);
            markerRun.setFontSize(11);

            // <li> ichidagi ichma-ich <ul>/<ol> — alohida chuqurlikda,
            // qolgan (matn) qismi esa shu qatorning o'zida.
            for (Node child : List.copyOf(li.childNodes())) {
                if (child instanceof Element childEl && ("ul".equalsIgnoreCase(childEl.tagName()) || "ol".equalsIgnoreCase(childEl.tagName()))) {
                    renderList(doc, childEl, uploadDir, depth + 1, "ol".equalsIgnoreCase(childEl.tagName()));
                } else {
                    renderListItemInline(doc, p, child, uploadDir, InlineStyle.PLAIN);
                }
            }
        }
    }

    // <li> matni ko'pincha <p>/<div> bilan o'ralgan bo'ladi (masalan
    // mammoth.js .docx import qilganda). Oddiy renderInlineNode buni
    // "bloklovchi" tag deb topib, doc.createParagraph() orqali HUJJATGA
    // yangi (bullet'siz) xatboshi qo'shib yuborardi — ro'yxat qatori
    // bo'sh, matn esa undan KEYIN, alohida qatorda chiqib qolardi. Shu
    // sabab <p>/<div> shu yerda TEKISLANADI (farzandlari to'g'ridan-
    // to'g'ri shu bitta bullet qatorining o'ziga yoziladi).
    private static void renderListItemInline(XWPFDocument doc, XWPFParagraph p, Node node, String uploadDir, InlineStyle style) {
        if (node instanceof Element el && ("p".equalsIgnoreCase(el.tagName()) || "div".equalsIgnoreCase(el.tagName()))) {
            for (Node child : List.copyOf(el.childNodes())) {
                renderListItemInline(doc, p, child, uploadDir, style);
            }
            return;
        }
        renderInlineNode(doc, p, node, uploadDir, style);
    }

    private static void renderTable(XWPFDocument doc, Element tableEl, String uploadDir) {
        List<Element> rows = tableEl.select("tr");
        if (rows.isEmpty()) {
            return;
        }
        int colCount = rows.stream()
                .mapToInt(r -> r.select("> td, > th").size())
                .max().orElse(1);
        if (colCount == 0) {
            return;
        }

        XWPFTable table = doc.createTable(rows.size(), colCount);
        table.setTableAlignment(TableRowAlign.CENTER);

        // MUHIM: POI createTable() "<w:tblGrid>"ni O'ZI YOZMAYDI — bu esa
        // OOXML sxemasida "<w:tbl>"ning MAJBURIY farzandi. Uning yo'qligi
        // POI'ning o'z XWPFDocument o'qishida sezilmaydi (o'zi yozgan
        // faylni "yumshoq" qayta o'qiydi), lekin BOSHQA har qanday qattiq
        // (standartga rioya qiluvchi) o'quvchi — Microsoft Word, LibreOffice,
        // python-docx va h.k. — buni BUZILGAN fayl deb rad etadi (haqiqiy
        // topilgan bug: python-docx bilan qo'lda tekshirilganda "required
        // <w:tblGrid> child element not present" xatosi bilan aniqlangan).
        CTTblGrid grid = table.getCTTbl().addNewTblGrid();
        int colWidthTwips = Math.max(600, 9000 / colCount);
        for (int c = 0; c < colCount; c++) {
            grid.addNewGridCol().setW(java.math.BigInteger.valueOf(colWidthTwips));
        }

        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = table.getRow(r);
            List<Element> cells = rows.get(r).select("> td, > th");
            for (int c = 0; c < colCount; c++) {
                XWPFTableCell cell = row.getCell(c);
                if (c < cells.size()) {
                    renderTableCellContent(doc, cell, cells.get(c), uploadDir);
                }
            }
        }

        // Jadvaldan keyin bo'sh xatboshi — matn jadvalga "yopishib" qolmasin deb.
        doc.createParagraph();
    }

    // <td>/<th> matni ko'pincha <p> bilan o'ralgan bo'ladi (masalan
    // mammoth.js .docx import qilganda — har bir katak kontenti ham
    // OOXML'da alohida abzats). Oddiy renderInlineChildren(doc, ...) buni
    // "bloklovchi" tag deb topib, doc.createParagraph() orqali HUJJATNING
    // O'ZIGA (katakning EMAS) yangi xatboshi qo'shib yuborardi — natijada
    // katak matni JADVALDAN TASHQARIDA chiqib qolardi (haqiqiy
    // foydalanuvchi shikoyati). Shu sabab har bir <p>/<div> farzand
    // katakning O'Z alohida xatboshisiga (cell.addParagraph()) yoziladi.
    private static void renderTableCellContent(XWPFDocument doc, XWPFTableCell cell, Element cellEl, String uploadDir) {
        List<Element> blockChildren = cellEl.children().stream()
                .filter(e -> "p".equalsIgnoreCase(e.tagName()) || "div".equalsIgnoreCase(e.tagName()))
                .toList();

        // POI har bir yangi katakka avtomatik bitta bo'sh xatboshi qo'yadi
        // — birinchi paragraf uchun o'sha xatboshining o'zi ishlatiladi
        // (ustma-ust bo'sh qatorlar hosil bo'lmasin deb).
        if (blockChildren.isEmpty()) {
            XWPFParagraph p = cell.getParagraphs().get(0);
            renderInlineChildren(doc, p, cellEl, uploadDir, InlineStyle.PLAIN);
            return;
        }

        boolean first = true;
        for (Element block : blockChildren) {
            XWPFParagraph p = first ? cell.getParagraphs().get(0) : cell.addParagraph();
            first = false;
            renderInlineChildren(doc, p, block, uploadDir, InlineStyle.PLAIN);
        }
    }

    // ===================== Inline (qator ichi) darajasi =====================

    // Natijada qaysi xatboshida to'xtaganini qaytaradi — rasm/PPT kabi
    // "bloklovchi" elementlar ICHIDA uchrasa, davomi UCHUN yangi xatboshi
    // ochiladi (aks holda docx'dagi xatboshilar tartibi buzilardi: POI
    // createParagraph() doim HUJJAT OXIRIGA qo'shadi, shu sabab avval
    // yaratilgan xatboshiga keyinroq run qo'shish uni "orqaga" surib
    // qo'ymaydi — lekin MANTIQIY tartib matn->rasm->matn bo'lishi kerak
    // bo'lsa, matnning IKKINCHI qismi ALBATTA yangi (rasmdan keyingi)
    // xatboshida bo'lishi shart).
    private static XWPFParagraph renderInlineChildren(XWPFDocument doc, XWPFParagraph paragraph, Element parent, String uploadDir, InlineStyle style) {
        XWPFParagraph current = paragraph;
        for (Node child : List.copyOf(parent.childNodes())) {
            current = renderInlineNodeAndContinue(doc, current, child, uploadDir, style);
        }
        return current;
    }

    private static void renderInlineNode(XWPFDocument doc, XWPFParagraph paragraph, Node node, String uploadDir, InlineStyle style) {
        renderInlineNodeAndContinue(doc, paragraph, node, uploadDir, style);
    }

    private static XWPFParagraph renderInlineNodeAndContinue(XWPFDocument doc, XWPFParagraph paragraph, Node node, String uploadDir, InlineStyle style) {
        if (node instanceof TextNode tn) {
            String text = tn.text();
            if (!text.isEmpty()) {
                addTextRun(paragraph, style, text);
            }
            return paragraph;
        }
        if (!(node instanceof Element el)) {
            return paragraph;
        }

        String tag = el.tagName().toLowerCase();
        switch (tag) {
            case "b", "strong" -> {
                return renderInlineChildren(doc, paragraph, el, uploadDir, style.withBold());
            }
            case "i", "em" -> {
                return renderInlineChildren(doc, paragraph, el, uploadDir, style.withItalic());
            }
            case "u" -> {
                return renderInlineChildren(doc, paragraph, el, uploadDir, style.withUnderline());
            }
            case "s", "strike", "del" -> {
                return renderInlineChildren(doc, paragraph, el, uploadDir, style.withStrike());
            }
            case "a" -> {
                String href = el.attr("href");
                return renderInlineChildren(doc, paragraph, el, uploadDir, style.withLink(href.isBlank() ? null : href));
            }
            case "br" -> {
                paragraph.createRun().addBreak();
                return paragraph;
            }
            case "img" -> {
                XWPFRun run = paragraph.createRun();
                DocxImageUtil.insertInlinePicture(run, uploadDir, el.attr("src"), extractWidthPx(el));
                return paragraph;
            }
            case "span" -> {
                if (el.hasClass("rich-img-handle") || el.hasClass("rich-ppt-nav") || el.hasClass("rich-ppt-counter")
                        || el.hasClass("rich-video-caption") || el.hasClass("embed-caption")) {
                    // Faqat tahrirlash oynasidagi boshqaruv elementlari
                    // (sudrab o'lchamlash tutqichi, slayd navigatsiyasi/
                    // hisoblagichi) — hujjatga umuman kerak emas.
                    return paragraph;
                }
                if (isPptWrap(el)) {
                    renderPptSlides(doc, el, uploadDir);
                    return doc.createParagraph();
                }
                if (isImgWrap(el)) {
                    renderStandaloneImage(doc, el, uploadDir);
                    return doc.createParagraph();
                }
                return renderInlineChildren(doc, paragraph, el, uploadDir, style);
            }
            case "p", "div", "ul", "ol", "table", "h1", "h2", "h3", "h4", "h5", "h6" -> {
                // Matn ichida "bloklovchi" tag uchrab qolsa (masalan
                // ba'zi editorlar/mammoth chiqargan ichma-ich <div>) —
                // yangi xatboshidan davom ettiramiz.
                renderBlock(doc, el, uploadDir);
                return doc.createParagraph();
            }
            default -> {
                return renderInlineChildren(doc, paragraph, el, uploadDir, style);
            }
        }
    }

    private static void addTextRun(XWPFParagraph paragraph, InlineStyle style, String text) {
        XWPFRun run = style.linkUrl() != null
                ? paragraph.createHyperlinkRun(style.linkUrl())
                : paragraph.createRun();
        run.setText(text);
        run.setBold(style.bold());
        run.setItalic(style.italic());
        run.setFontSize(11);
        if (style.underline()) {
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
        if (style.strike()) {
            run.setStrikeThrough(true);
        }
        if (style.linkUrl() != null) {
            run.setColor("0563C1");
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
    }
}
