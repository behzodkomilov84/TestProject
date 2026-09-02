package behzoddev.testproject.service;

import behzoddev.testproject.entity.enums.CourseSectionContentFormat;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kurs mavzusi matnini (HTML) .docx'ga o'girish — "imkon qadar maksimal"
 * formatlash: qalin/kursiv, giperssilkalar, ro'yxatlar, jadvallar,
 * rasmlar (disk VA base64 ikkalasi ham) va PPT slaydlar ketma-ketligi.
 */
class HtmlToDocxConverterTest {

    @TempDir
    Path tempDir;

    private static byte[] realPngBytes() throws IOException {
        return realPngBytes(0x000000);
    }

    // POI'ning o'zi BAYT-BAYT bir xil rasm ma'lumotini ADD qilinganda
    // avtomatik DEDUPLIKATSIYA qiladi (bir xil checksum — bitta rasm
    // sifatida saqlanadi, ikkinchi marta qo'shilmaydi) — shu sabab bir
    // nechta ALOHIDA rasm sifatida hisoblanishi kerak bo'lgan testlarda
    // (masalan PPT slaydlar) har birining piksel rangi BOSHQA bo'lishi
    // shart, aks holda haqiqiy kodda hech qanday bug bo'lmasa ham test
    // "kamroq rasm topildi" deb yolg'ondan yiqilib qoladi.
    private static byte[] realPngBytes(int rgb) throws IOException {
        BufferedImage image = new BufferedImage(10, 6, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, rgb);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    @Test
    void convert_plainFormat_writesOneParagraphPerLine() {
        XWPFDocument doc = new XWPFDocument();
        HtmlToDocxConverter.convert(doc, "Birinchi qator\nIkkinchi qator", CourseSectionContentFormat.PLAIN, tempDir.toString());

        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        assertThat(paragraphs).hasSize(2);
        assertThat(paragraphs.get(0).getText()).isEqualTo("Birinchi qator");
        assertThat(paragraphs.get(1).getText()).isEqualTo("Ikkinchi qator");
    }

    @Test
    void convert_boldItalicUnderline_appliesRunFormatting() {
        XWPFDocument doc = new XWPFDocument();
        HtmlToDocxConverter.convert(doc,
                "<p>oddiy <b>qalin</b> <i>kursiv</i> <u>tagiga chizilgan</u></p>",
                CourseSectionContentFormat.HTML, tempDir.toString());

        List<XWPFRun> runs = doc.getParagraphs().get(0).getRuns();
        assertThat(runs).extracting(XWPFRun::text).containsExactly("oddiy ", "qalin", " ", "kursiv", " ", "tagiga chizilgan");
        assertThat(runs.get(1).isBold()).isTrue();
        assertThat(runs.get(3).isItalic()).isTrue();
        assertThat(runs.get(5).getUnderline().toString()).isNotEqualTo("NONE");
    }

    @Test
    void convert_hyperlink_createsColoredUnderlinedHyperlinkRun() {
        XWPFDocument doc = new XWPFDocument();
        HtmlToDocxConverter.convert(doc,
                "<p>Havola: <a href=\"https://study-grow.uz\">saytga</a></p>",
                CourseSectionContentFormat.HTML, tempDir.toString());

        List<XWPFRun> runs = doc.getParagraphs().get(0).getRuns();
        XWPFRun linkRun = runs.stream().filter(r -> "saytga".equals(r.text())).findFirst().orElseThrow();
        assertThat(linkRun.getColor()).isEqualTo("0563C1");
        assertThat(linkRun.getUnderline().toString()).isNotEqualTo("NONE");
    }

    @Test
    void convert_unorderedList_addsBulletPrefixPerItem() {
        XWPFDocument doc = new XWPFDocument();
        HtmlToDocxConverter.convert(doc,
                "<ul><li>Birinchi</li><li>Ikkinchi</li></ul>",
                CourseSectionContentFormat.HTML, tempDir.toString());

        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        assertThat(paragraphs).hasSize(2);
        assertThat(paragraphs.get(0).getText()).isEqualTo("• Birinchi");
        assertThat(paragraphs.get(1).getText()).isEqualTo("• Ikkinchi");
    }

    @Test
    void convert_orderedList_numbersSequentially() {
        XWPFDocument doc = new XWPFDocument();
        HtmlToDocxConverter.convert(doc,
                "<ol><li>Birinchi</li><li>Ikkinchi</li><li>Uchinchi</li></ol>",
                CourseSectionContentFormat.HTML, tempDir.toString());

        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        assertThat(paragraphs).extracting(XWPFParagraph::getText)
                .containsExactly("1. Birinchi", "2. Ikkinchi", "3. Uchinchi");
    }

    @Test
    void convert_table_createsTableWithMatchingCellText() {
        XWPFDocument doc = new XWPFDocument();
        HtmlToDocxConverter.convert(doc,
                "<table><tr><td>A1</td><td>B1</td></tr><tr><td>A2</td><td>B2</td></tr></table>",
                CourseSectionContentFormat.HTML, tempDir.toString());

        assertThat(doc.getTables()).hasSize(1);
        XWPFTable table = doc.getTables().get(0);
        assertThat(table.getRows()).hasSize(2);
        assertThat(table.getRow(0).getCell(0).getText()).isEqualTo("A1");
        assertThat(table.getRow(0).getCell(1).getText()).isEqualTo("B1");
        assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("A2");
        assertThat(table.getRow(1).getCell(1).getText()).isEqualTo("B2");

        // Haqiqiy topilgan bug (python-docx bilan qo'lda tekshirilganda
        // aniqlangan — POI createTable() "<w:tblGrid>"ni O'ZI YOZMAYDI,
        // garchi OOXML sxemasida bu "<w:tbl>"ning MAJBURIY farzandi
        // bo'lsa ham): uning yo'qligi POI'ning o'z XWPFDocument o'qishida
        // sezilmaydi, lekin Microsoft Word/LibreOffice/python-docx kabi
        // qattiq (sxemaga rioya qiluvchi) o'quvchilar faylni BUZILGAN deb
        // rad etadi. renderTable() shu sabab tblGrid'ni QO'LDA qo'shadi —
        // shu tekshiruv buni ANIQ tasdiqlaydi.
        assertThat(table.getCTTbl().getTblGrid()).isNotNull();
        assertThat(table.getCTTbl().getTblGrid().sizeOfGridColArray()).isEqualTo(2);
    }

    @Test
    void convert_standaloneImageAsBase64DataUri_embedsPicture() throws IOException {
        XWPFDocument doc = new XWPFDocument();
        String base64 = Base64.getEncoder().encodeToString(realPngBytes());
        String html = "<span class=\"rich-img-wrap\"><img src=\"data:image/png;base64," + base64 + "\"></span>";

        HtmlToDocxConverter.convert(doc, html, CourseSectionContentFormat.HTML, tempDir.toString());

        assertThat(doc.getAllPictures()).hasSize(1);
    }

    @Test
    void convert_pptSlideshow_embedsEverySlideInOrderWithCaption() throws IOException {
        Path coursesDir = Files.createDirectories(tempDir.resolve("courses"));
        Files.write(coursesDir.resolve("slide-1.png"), realPngBytes(0xFF0000));
        Files.write(coursesDir.resolve("slide-2.png"), realPngBytes(0x00FF00));
        Files.write(coursesDir.resolve("slide-3.png"), realPngBytes(0x0000FF));

        // courseDetail.js#insertPptSlideshowHtml — faqat 1-slayd <img>da
        // ko'rinadi, QOLGANI data-slides JSON'ida.
        String html = "<span class=\"rich-img-wrap rich-ppt-wrap\" "
                + "data-slides=\"[&quot;/uploads/courses/slide-1.png&quot;,&quot;/uploads/courses/slide-2.png&quot;,&quot;/uploads/courses/slide-3.png&quot;]\">"
                + "<img src=\"/uploads/courses/slide-1.png\"></span>";

        XWPFDocument doc = new XWPFDocument();
        HtmlToDocxConverter.convert(doc, html, CourseSectionContentFormat.HTML, tempDir.toString());

        assertThat(doc.getAllPictures()).hasSize(3);
        List<String> captions = doc.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .filter(t -> t.endsWith("-slayd:"))
                .toList();
        assertThat(captions).containsExactly("1-slayd:", "2-slayd:", "3-slayd:");
    }
}
