package behzoddev.testproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Savol, javob va izoh (commentary)ga rasm/video biriktirish uchun
 * fayllarni diskka saqlaydigan xizmat.
 * <p>
 * Har bir fayl saqlanishdan oldin ikki bosqichda tekshiriladi:
 * 1) Apache Tika orqali faylning HAQIQIY turi (magic-byte) aniqlanadi —
 *    client yuborgan Content-Type header'iga ishonib bo'lmaydi (masalan,
 *    ".jpg" deb nomlangan, lekin ichida .exe bo'lgan faylni ushlash uchun).
 * 2) {@link ClamAvScanService} orqali virus/zararli kod tekshiriladi
 *    (ClamAV yoqilgan bo'lsa — {@code app.upload.clamav.enabled}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final ClamAvScanService clamAvScanService;
    private final Tika tika = new Tika();

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"
    );
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS =
            List.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/ogg",
            // Tika magic-byte tekshiruvida video konteynerlar ba'zan shu
            // muqobil (lekin haqiqiy) turlar sifatida aniqlanadi — WebM
            // texnik jihatdan Matroska profili, OGG esa umumiy konteyner.
            "application/ogg", "video/x-matroska"
    );
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS =
            List.of(".mp4", ".webm", ".ogg", ".ogv");
    private static final long MAX_VIDEO_SIZE_BYTES = 50L * 1024 * 1024; // 50MB

    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * Savolga biriktirilgan rasmni "questions" ostki papkasiga saqlaydi.
     */
    public String storeQuestionImage(MultipartFile file) {
        return store(file, "questions", ALLOWED_IMAGE_TYPES, ALLOWED_IMAGE_EXTENSIONS,
                MAX_IMAGE_SIZE_BYTES, "❌Rasm hajmi 5MB dan katta bo'lishi mumkin emas.",
                "❌Faqat rasm fayllari (PNG, JPEG, WEBP, GIF) yuklash mumkin.");
    }

    /**
     * Izoh (commentary) uchun rasm — "commentary" ostki papkasiga saqlanadi.
     */
    public String storeCommentaryImage(MultipartFile file) {
        return store(file, "commentary", ALLOWED_IMAGE_TYPES, ALLOWED_IMAGE_EXTENSIONS,
                MAX_IMAGE_SIZE_BYTES, "❌Rasm hajmi 5MB dan katta bo'lishi mumkin emas.",
                "❌Faqat rasm fayllari (PNG, JPEG, WEBP, GIF) yuklash mumkin.");
    }

    /**
     * Izoh (commentary) uchun video — "commentary" ostki papkasiga saqlanadi.
     */
    public String storeCommentaryVideo(MultipartFile file) {
        return store(file, "commentary", ALLOWED_VIDEO_TYPES, ALLOWED_VIDEO_EXTENSIONS,
                MAX_VIDEO_SIZE_BYTES, "❌Video hajmi 50MB dan katta bo'lishi mumkin emas.",
                "❌Faqat video fayllari (MP4, WEBM, OGG) yuklash mumkin.");
    }

    private String store(
            MultipartFile file,
            String subDir,
            Set<String> allowedContentTypes,
            List<String> allowedExtensions,
            long maxSizeBytes,
            String sizeErrorMessage,
            String typeErrorMessage
    ) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("❌Fayl tanlanmagan.");
        }

        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(sizeErrorMessage);
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(typeErrorMessage);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("Faylni o'qishda xatolik", e);
            throw new IllegalStateException("❌Faylni o'qib bo'lmadi.", e);
        }

        // 1) Magic-byte tekshiruvi — faylning HAQIQIY turi client yuborgan
        // Content-Type header bilan (yuqorida tekshirilgan) mos kelishi shart.
        // Aks holda, masalan ".jpg" nomli, lekin ichida boshqa narsa bo'lgan
        // fayl ushlanadi.
        String detectedType = tika.detect(content);
        if (!allowedContentTypes.contains(detectedType.toLowerCase())) {
            log.warn("Fayl turi mos kelmadi: client Content-Type='{}', haqiqiy (Tika)='{}', fayl='{}'",
                    contentType, detectedType, file.getOriginalFilename());
            throw new IllegalArgumentException(typeErrorMessage);
        }

        // 2) Virus/zararli kod tekshiruvi (ClamAV yoqilgan bo'lsa).
        clamAvScanService.scan(content, file.getOriginalFilename());

        try {
            Path targetDir = Path.of(uploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String extension = extractExtension(file.getOriginalFilename(), allowedExtensions);
            String newFileName = UUID.randomUUID() + extension;

            Path targetFile = targetDir.resolve(newFileName).normalize();

            // Path traversal'dan himoya — fayl haqiqatan ham kutilgan papka ichida ekanligini tekshiramiz.
            if (!targetFile.startsWith(targetDir)) {
                throw new IllegalArgumentException("❌Noto'g'ri fayl nomi.");
            }

            Files.write(targetFile, content);

            return "/uploads/" + subDir + "/" + newFileName;

        } catch (IOException e) {
            log.error("Faylni saqlashda xatolik", e);
            throw new IllegalStateException("❌Faylni saqlab bo'lmadi.", e);
        }
    }

    private String extractExtension(String originalFilename, List<String> allowedExtensions) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }

        int dotIndex = originalFilename.lastIndexOf('.');

        if (dotIndex < 0) {
            return "";
        }

        String ext = originalFilename.substring(dotIndex).toLowerCase();
        return allowedExtensions.contains(ext) ? ext : "";
    }
}
