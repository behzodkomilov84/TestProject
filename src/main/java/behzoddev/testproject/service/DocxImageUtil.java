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
    // Sahifaga sig'ishi uchun eng katta kenglik (~3.8 dyuym) — bundan
    // kattaroq rasmlar shu kenglikkacha (nisbatini saqlagan holda) kichraytiriladi.
    private static final int MAX_WIDTH_EMU = 3_500_000;

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
        addPicture(imageRun, resolved);
    }

    /**
     * HtmlToDocxConverter uchun — kurs mavzusi matni ICHIDAGI (inline)
     * rasmni JORIY run'ga (joriy xatboshi oqimida, matn bilan bir
     * qatorda) qo'shadi. {@code overrideWidthPx} — rich-text-editor'da
     * qo'lda o'lchami o'zgartirilgan bo'lsa (rich-img-wrap {@code <img
     * style="width:...px">}), o'sha kenglik ishlatiladi; {@code null}
     * bo'lsa — rasmning haqiqiy o'lchami (sahifa kengligiga qadar
     * kichraytirilib). Muvaffaqiyatli qo'shilsa {@code true} qaytaradi.
     */
    static boolean insertInlinePicture(XWPFRun run, String uploadDir, String src, Integer overrideWidthPx) {
        ResolvedImage resolved = resolve(uploadDir, src);
        if (resolved == null) {
            return false;
        }
        return addPicture(run, resolved, overrideWidthPx);
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

    private static boolean addPicture(XWPFRun run, ResolvedImage resolved) {
        return addPicture(run, resolved, null);
    }

    private static boolean addPicture(XWPFRun run, ResolvedImage resolved, Integer overrideWidthPx) {
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
                    ? Math.min(MAX_WIDTH_EMU, overrideWidthPx * EMU_PER_PIXEL)
                    : Math.min(MAX_WIDTH_EMU, naturalWidthEmu);
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
