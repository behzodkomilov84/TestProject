package behzoddev.testproject.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// WordService, ExamVariantService VA HtmlToDocxConverter uchun umumiy —
// rasmni .docx'ga qo'shadi. Ikki manbani biladi: "/uploads/..." (diskdagi
// yuklangan fayl — savol/javob rasmlari, FileStorageService) va
// "data:image/...;base64,..." (kurs mavzusi matniga .docx'dan import
// qilinganda mammoth.js base64 sifatida ko'mib qo'yadigan rasmlar,
// courseDetail.js#importDocxFile — bular diskda YO'Q, faqat HTML ichida).
// Oddiy statik utility — DI shart emas.
@Slf4j
final class DocxImageUtil {

    private DocxImageUtil() {
    }

    // POI/Word "haqiqiy" qo'llab-quvvatlaydigan formatlar. WEBP/HEIC bu
    // ro'yxatda YO'Q — HEIC yuklashda avtomatik JPEG'ga o'giriladi
    // (FileStorageService#convertHeicToJpeg), lekin WEBP o'girilmaydi,
    // shu sabab WEBP rasm eksportga TUSHMAYDI (pastda — sekin-asta
    // o'tkazib yuboriladi, butun eksportni to'xtatmaydi).
    private static final Map<String, Integer> POI_PICTURE_TYPES_BY_EXTENSION = Map.of(
            ".png", XWPFDocument.PICTURE_TYPE_PNG,
            ".jpg", XWPFDocument.PICTURE_TYPE_JPEG,
            ".jpeg", XWPFDocument.PICTURE_TYPE_JPEG,
            ".gif", XWPFDocument.PICTURE_TYPE_GIF,
            ".bmp", XWPFDocument.PICTURE_TYPE_BMP
    );

    // "data:image/<mime-subtype>;base64,..." ichidagi mime-subtype ->
    // POI rasm turi (yuqoridagi kengaytma xaritasi bilan bir xil ro'yxat).
    private static final Map<String, Integer> POI_PICTURE_TYPES_BY_MIME_SUBTYPE = Map.of(
            "png", XWPFDocument.PICTURE_TYPE_PNG,
            "jpeg", XWPFDocument.PICTURE_TYPE_JPEG,
            "jpg", XWPFDocument.PICTURE_TYPE_JPEG,
            "gif", XWPFDocument.PICTURE_TYPE_GIF,
            "bmp", XWPFDocument.PICTURE_TYPE_BMP
    );

    private static final Pattern DATA_URI_PATTERN =
            Pattern.compile("^data:image/([a-zA-Z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);

    // 96 DPI'dagi standart piksel->EMU nisbati (Word/OOXML o'lchov birligi).
    private static final int EMU_PER_PIXEL = 9525;
    // Savol/javob rasmlari uchun eng katta kenglik (~3.8 dyuym) — test
    // varag'ida rasm butun sahifani egallab ketmasin deb ATAYLAB kichik
    // (insertImageIfPresent — WordService/ExamVariantService/
    // CourseWordExportService'ning TESTLAR qismi).
    private static final int MAX_WIDTH_EMU = 3_500_000;
    // Kurs mavzusi matni ICHIDAGI (inline) rasm uchun — hujjatning HAQIQIY
    // varag'i kengligi (Letter, standart 1 dyuymlik chetlar bilan: 8.5in -
    // 2*1in = 6.5in). Bu yerdagi hujjatlarda (XWPFDocument()) sahifa
    // o'lchami ANIQ belgilanmagan (Word o'zining standart sozlamasini
    // qo'llaydi) — shu standart bilan BIR XIL songa moslashtirilgan.
    // Foydalanuvchi ANIQ shikoyati: kursda (brauzerda) rasm butun eni
    // bilan (konteynerga to'liq) chiqadi, lekin eksportda tor bo'lib
    // qolardi — chunki avval BARCHA (o'lchami o'zgartirilmagan) rasmlar
    // shu YUQORIDAGI kichik (~3.8in) chegaraga tushib qolardi.
    private static final int PAGE_CONTENT_WIDTH_EMU = 5_943_600;

    private record ResolvedImage(byte[] bytes, int pictureType, String filenameHint) {
    }

    /**
     * Savol/javobga biriktirilgan rasmni (agar bo'lsa) joriy hujjatga,
     * YANGI alohida xatboshi sifatida qo'shadi. {@code imageUrl} —
     * FileStorageService qaytargan "/uploads/..." formatidagi manzil;
     * fayl mahalliy diskdan (uploadDir orqali) o'qiladi, tarmoq so'rovi
     * YO'Q. Rasm topilmasa/o'qib bo'lmasa/format qo'llab-quvvatlanmasa —
     * jim o'tkazib yuboriladi (log'ga ogohlantirish bilan) — bitta
     * muammoli rasm butun eksportni to'xtatmasligi kerak.
     */
    static void insertImageIfPresent(XWPFDocument doc, String uploadDir, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        ResolvedImage resolved = resolve(uploadDir, imageUrl);
        if (resolved == null) {
            return;
        }

        XWPFParagraph imagePara = doc.createParagraph();
        imagePara.setSpacingBefore(60);
        imagePara.setSpacingAfter(100);
        XWPFRun imageRun = imagePara.createRun();
        addPicture(imageRun, resolved, null, MAX_WIDTH_EMU);
    }

    /**
     * HtmlToDocxConverter uchun — kurs mavzusi matni ICHIDAGI (inline)
     * rasmni JORIY run'ga (joriy xatboshi oqimida, matn bilan bir
     * qatorda) qo'shadi. {@code overrideWidthPx} — rich-text-editor'da
     * qo'lda o'lchami o'zgartirilgan bo'lsa (rich-img-wrap {@code <img
     * style="width:...px">}), o'sha kenglik ishlatiladi; {@code null}
     * bo'lsa — rasmning haqiqiy o'lchami (sahifa kengligiga qadar
     * kichraytirilib). Ikkala holatda ham — kurs (brauzer) ko'rinishida
     * rasm o'z konteyneri (sahifa) eniga qadar cho'zilishi mumkin, shu
     * sabab bu yerda ANIQ sahifa kengligi (PAGE_CONTENT_WIDTH_EMU) chegara
     * sifatida olinadi — savol/javob rasmlaridagi kichikroq chegara EMAS
     * (aks holda kursda keng chiqqan rasm eksportda tor bo'lib qolardi —
     * haqiqiy foydalanuvchi shikoyati). Muvaffaqiyatli qo'shilsa
     * {@code true} qaytaradi.
     */
    static boolean insertInlinePicture(XWPFRun run, String uploadDir, String src, Integer overrideWidthPx) {
        ResolvedImage resolved = resolve(uploadDir, src);
        if (resolved == null) {
            return false;
        }
        return addPicture(run, resolved, overrideWidthPx, PAGE_CONTENT_WIDTH_EMU);
    }

    private static ResolvedImage resolve(String uploadDir, String src) {
        if (src.startsWith("data:")) {
            return resolveDataUri(src);
        }
        if (src.startsWith("/uploads/")) {
            return resolveDiskFile(uploadDir, src);
        }
        // Tashqi (masalan boshqa saytdagi) rasm URL'lari — tarmoq so'rovi
        // ATAYLAB qilinmaydi (eksport paytida tashqi serverga ishonchsiz
        // so'rov yubormaslik uchun), shu sabab bunday rasm eksportga
        // TUSHMAYDI.
        return null;
    }

    private static ResolvedImage resolveDataUri(String src) {
        Matcher m = DATA_URI_PATTERN.matcher(src);
        if (!m.matches()) {
            return null;
        }
        Integer pictureType = POI_PICTURE_TYPES_BY_MIME_SUBTYPE.get(m.group(1).toLowerCase());
        if (pictureType == null) {
            log.warn("Eksportda qo'llab-quvvatlanmaydigan (base64) rasm formati: {}", m.group(1));
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(m.group(2));
            return new ResolvedImage(bytes, pictureType, "image");
        } catch (IllegalArgumentException e) {
            log.warn("Base64 rasmni dekodlashda xatolik", e);
            return null;
        }
    }

    private static ResolvedImage resolveDiskFile(String uploadDir, String src) {
        try {
            Path filePath = Path.of(uploadDir).resolve(src.substring("/uploads/".length())).normalize();
            if (!Files.exists(filePath)) {
                log.warn("Eksport uchun rasm topilmadi: {}", filePath);
                return null;
            }

            Integer pictureType = POI_PICTURE_TYPES_BY_EXTENSION.get(extractExtension(filePath.getFileName().toString()));
            if (pictureType == null) {
                log.warn("Eksportda qo'llab-quvvatlanmaydigan rasm formati: {}", filePath);
                return null;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            return new ResolvedImage(bytes, pictureType, filePath.getFileName().toString());
        } catch (IOException e) {
            log.warn("Rasmni eksportga qo'shishda xatolik: {}", src, e);
            return null;
        }
    }

    private static boolean addPicture(XWPFRun run, ResolvedImage resolved, Integer overrideWidthPx, int maxWidthEmu) {
        try {
            BufferedImage image;
            try (ByteArrayInputStream probe = new ByteArrayInputStream(resolved.bytes())) {
                image = ImageIO.read(probe);
            }
            if (image == null || image.getWidth() <= 0) {
                log.warn("Rasm o'lchamini aniqlab bo'lmadi: {}", resolved.filenameHint());
                return false;
            }

            int naturalWidthEmu = image.getWidth() * EMU_PER_PIXEL;
            int widthEmu = overrideWidthPx != null
                    ? Math.min(maxWidthEmu, overrideWidthPx * EMU_PER_PIXEL)
                    : Math.min(maxWidthEmu, naturalWidthEmu);
            int heightEmu = (int) ((long) widthEmu * image.getHeight() / image.getWidth());

            try (ByteArrayInputStream imgStream = new ByteArrayInputStream(resolved.bytes())) {
                run.addPicture(imgStream, resolved.pictureType(), resolved.filenameHint(), widthEmu, heightEmu);
            }
            return true;
        } catch (IOException | InvalidFormatException e) {
            log.warn("Rasmni eksportga qo'shishda xatolik: {}", resolved.filenameHint(), e);
            return false;
        }
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
