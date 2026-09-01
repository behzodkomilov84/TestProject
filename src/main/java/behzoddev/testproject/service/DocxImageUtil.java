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
import java.util.Map;

// WordService va ExamVariantService IKKALASI uchun umumiy — savol/javobga
// biriktirilgan rasmni .docx'ga qo'shadi (avval bu ikkala eksportda ham
// rasmlar UMUMAN tushmasdi — faqat matn). Har ikkala servis ham shu
// yerdagi bitta STATIK metodni chaqiradi (DI shart emas — oddiy utility).
@Slf4j
final class DocxImageUtil {

    private DocxImageUtil() {
    }

    // POI/Word "haqiqiy" qo'llab-quvvatlaydigan formatlar. WEBP/HEIC bu
    // ro'yxatda YO'Q — HEIC yuklashda avtomatik JPEG'ga o'giriladi
    // (FileStorageService#convertHeicToJpeg), lekin WEBP o'girilmaydi,
    // shu sabab WEBP rasm eksportga TUSHMAYDI (pastda — sekin-asta
    // o'tkazib yuboriladi, butun eksportni to'xtatmaydi).
    private static final Map<String, Integer> POI_PICTURE_TYPES = Map.of(
            ".png", XWPFDocument.PICTURE_TYPE_PNG,
            ".jpg", XWPFDocument.PICTURE_TYPE_JPEG,
            ".jpeg", XWPFDocument.PICTURE_TYPE_JPEG,
            ".gif", XWPFDocument.PICTURE_TYPE_GIF,
            ".bmp", XWPFDocument.PICTURE_TYPE_BMP
    );

    // 96 DPI'dagi standart piksel->EMU nisbati (Word/OOXML o'lchov birligi).
    private static final int EMU_PER_PIXEL = 9525;
    // Sahifaga sig'ishi uchun eng katta kenglik (~3.8 dyuym) — bundan
    // kattaroq rasmlar shu kenglikkacha (nisbatini saqlagan holda) kichraytiriladi.
    private static final int MAX_WIDTH_EMU = 3_500_000;

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
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("/uploads/")) {
            return;
        }

        try {
            Path filePath = Path.of(uploadDir).resolve(imageUrl.substring("/uploads/".length())).normalize();
            if (!Files.exists(filePath)) {
                log.warn("Eksport uchun rasm topilmadi: {}", filePath);
                return;
            }

            Integer pictureType = POI_PICTURE_TYPES.get(extractExtension(filePath.getFileName().toString()));
            if (pictureType == null) {
                log.warn("Eksportda qo'llab-quvvatlanmaydigan rasm formati: {}", filePath);
                return;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            BufferedImage image;
            try (ByteArrayInputStream probe = new ByteArrayInputStream(bytes)) {
                image = ImageIO.read(probe);
            }
            if (image == null || image.getWidth() <= 0) {
                log.warn("Rasm o'lchamini aniqlab bo'lmadi: {}", filePath);
                return;
            }

            int widthEmu = Math.min(MAX_WIDTH_EMU, image.getWidth() * EMU_PER_PIXEL);
            int heightEmu = (int) ((long) widthEmu * image.getHeight() / image.getWidth());

            XWPFParagraph imagePara = doc.createParagraph();
            imagePara.setSpacingBefore(60);
            imagePara.setSpacingAfter(100);
            XWPFRun imageRun = imagePara.createRun();
            try (ByteArrayInputStream imgStream = new ByteArrayInputStream(bytes)) {
                imageRun.addPicture(imgStream, pictureType, filePath.getFileName().toString(), widthEmu, heightEmu);
            }
        } catch (IOException | InvalidFormatException e) {
            log.warn("Rasmni eksportga qo'shishda xatolik: {}", imageUrl, e);
        }
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
