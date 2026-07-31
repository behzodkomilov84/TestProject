package behzoddev.testproject.service;

import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"
    );
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS =
            List.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/ogg"
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

            file.transferTo(targetFile);

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
