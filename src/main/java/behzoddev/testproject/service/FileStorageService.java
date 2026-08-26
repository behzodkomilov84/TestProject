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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

    // HEIC/HEIF — iPhone'ning standart rasm formati. Ko'pchilik brauzer
    // (Chrome, Firefox, Edge) buni <img> orqali UMUMAN ko'rsata olmaydi
    // (faqat Safari/iOS qo'llab-quvvatlaydi) — shu sabab shunchaki ruxsat
    // berish YETARLI EMAS, fayl haqiqatan ko'rinishi uchun yuklashda avval
    // JPEG'ga o'girib olinishi shart (pastda, convertHeicToJpegIfNeeded()).
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif",
            "image/heic", "image/heif"
    );
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS =
            List.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".heic", ".heif");
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    // PPT/PPTX taqdimot — "🎞 PPT qo'shish" (rich-toolbar, kurs bo'limi
    // matni ichiga slaydlar sifatida qo'shish) uchun. Tika OOXML
    // konteynerni (.pptx) odatda to'g'ri aniqlaydi (ichidagi
    // [Content_Types].xml orqali), shu sabab umumiy "application/zip"
    // muqobil sifatida QO'SHILMAGAN — bu haqiqiy .pptx tekshiruvini
    // zaiflashtirmasligi uchun.
    private static final Set<String> ALLOWED_PPT_TYPES = Set.of(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );
    private static final List<String> ALLOWED_PPT_EXTENSIONS = List.of(".ppt", ".pptx");
    private static final long MAX_PPT_SIZE_BYTES = 50L * 1024 * 1024; // 50MB

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/ogg",
            // Tika magic-byte tekshiruvida video konteynerlar ba'zan shu
            // muqobil (lekin haqiqiy) turlar sifatida aniqlanadi — WebM
            // texnik jihatdan Matroska profili, OGG esa umumiy konteyner.
            // video/quicktime — telefon/Instagram'dan yuklab olingan ko'plab
            // ".mp4" fayllar ichida QuickTime uslubidagi "ftyp" belgisidan
            // foydalanadi (garchi kengaytmasi .mp4 va oddiy pleyerlarda
            // muammosiz ijro etilsa ham), shuning uchun Tika ularni
            // "video/mp4" emas, "video/quicktime" deb aniqlaydi — ruxsat
            // berilmasa, mutlaqo yaroqli .mp4 fayllar rad etiladi.
            "application/ogg", "video/x-matroska", "video/quicktime"
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
                MAX_IMAGE_SIZE_BYTES, "❌Rasm hajmi 10MB dan katta bo'lishi mumkin emas.",
                "❌Faqat rasm fayllari (PNG, JPEG, WEBP, GIF) yuklash mumkin.");
    }

    /**
     * Izoh (commentary) uchun rasm — "commentary" ostki papkasiga saqlanadi.
     */
    public String storeCommentaryImage(MultipartFile file) {
        return store(file, "commentary", ALLOWED_IMAGE_TYPES, ALLOWED_IMAGE_EXTENSIONS,
                MAX_IMAGE_SIZE_BYTES, "❌Rasm hajmi 10MB dan katta bo'lishi mumkin emas.",
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

    /**
     * Kurs bo'limi uchun video (UPLOAD manba) — "courses" ostki papkasiga saqlanadi.
     */
    public String storeCourseVideo(MultipartFile file) {
        return store(file, "courses", ALLOWED_VIDEO_TYPES, ALLOWED_VIDEO_EXTENSIONS,
                MAX_VIDEO_SIZE_BYTES, "❌Video hajmi 50MB dan katta bo'lishi mumkin emas.",
                "❌Faqat video fayllari (MP4, WEBM, OGG) yuklash mumkin.");
    }

    /**
     * Kurs muqova rasmi — "courses" ostki papkasiga saqlanadi.
     */
    public String storeCourseCoverImage(MultipartFile file) {
        return store(file, "courses", ALLOWED_IMAGE_TYPES, ALLOWED_IMAGE_EXTENSIONS,
                MAX_IMAGE_SIZE_BYTES, "❌Rasm hajmi 10MB dan katta bo'lishi mumkin emas.",
                "❌Faqat rasm fayllari (PNG, JPEG, WEBP, GIF) yuklash mumkin.");
    }

    /**
     * Kurs bo'limi matni (rich-toolbar) ichiga qo'shiladigan rasm —
     * "courses" ostki papkasiga saqlanadi.
     */
    public String storeCourseSectionImage(MultipartFile file) {
        return store(file, "courses", ALLOWED_IMAGE_TYPES, ALLOWED_IMAGE_EXTENSIONS,
                MAX_IMAGE_SIZE_BYTES, "❌Rasm hajmi 10MB dan katta bo'lishi mumkin emas.",
                "❌Faqat rasm fayllari (PNG, JPEG, WEBP, GIF) yuklash mumkin.");
    }

    /**
     * PPT/PPTX taqdimotni kurs bo'limi matni ichiga (rich-toolbar, "🎞 PPT
     * qo'shish") slaydlar sifatida qo'shish uchun — LibreOffice orqali
     * PDF'ga, so'ng {@code pdftoppm} orqali har bir sahifa alohida PNG
     * rasmga aylantiriladi ("courses" ostki papkasiga saqlanadi). Natija —
     * tartiblangan slayd rasm URL'lari ro'yxati (birinchi elementi —
     * 1-slayd).
     */
    public List<String> storeCoursePptSlides(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("❌Fayl tanlanmagan.");
        }

        if (file.getSize() > MAX_PPT_SIZE_BYTES) {
            throw new IllegalArgumentException("❌Taqdimot hajmi 50MB dan katta bo'lishi mumkin emas.");
        }

        String contentType = file.getContentType();
        boolean declaredTypeUnknown = contentType == null || contentType.equalsIgnoreCase("application/octet-stream");
        if (!declaredTypeUnknown && !ALLOWED_PPT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("❌Faqat PowerPoint fayllari (.ppt, .pptx) yuklash mumkin.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("Faylni o'qishda xatolik", e);
            throw new IllegalStateException("❌Faylni o'qib bo'lmadi.", e);
        }

        String detectedType = tika.detect(content);
        boolean recognizedByTika = ALLOWED_PPT_TYPES.contains(detectedType.toLowerCase());
        // Ba'zi .pptx fayllar (haqiqiy PowerPoint bilan emas, balki biror
        // avtomatik generator/kutubxona bilan yaratilgan — production'da
        // TASDIQLANGAN haqiqiy holat, real faylda tekshirilgan) Tika
        // kutgan to'liq ichki metama'lumotga ega bo'lmaydi, shu sabab
        // Tika ularni aniq PowerPoint sifatida emas, umumiy — yoki
        // "application/zip", yoki "application/x-tika-ooxml" ("bu OOXML
        // ekanini bildim, lekin aniq QAYSI turi ekanini bilolmadim" degan
        // Tika'ning o'z fallback turi) — deb aniqlashi mumkin, garchi
        // LibreOffice ularni MUAMMOSIZ ochsa ham. Shu holatda Tika'ning
        // umumiy taxminiga emas, ZIP FAYL ICHIDA "ppt/presentation.xml"
        // borligini TO'G'RIDAN-TO'G'RI tekshiramiz — bu "bu chindan ham
        // PowerPoint paketi"ligini Tika taxminidan ANIQROQ tasdiqlaydi
        // (soxta zip'lar hali ham rad etiladi).
        boolean looksLikePptxZip = !recognizedByTika
                && ("application/zip".equalsIgnoreCase(detectedType)
                    || "application/x-tika-ooxml".equalsIgnoreCase(detectedType))
                && containsPptxPresentationXml(content);
        if (!recognizedByTika && !looksLikePptxZip) {
            log.warn("Fayl turi mos kelmadi: client Content-Type='{}', haqiqiy (Tika)='{}', fayl='{}'",
                    contentType, detectedType, file.getOriginalFilename());
            throw new IllegalArgumentException("❌Faqat PowerPoint fayllari (.ppt, .pptx) yuklash mumkin.");
        }

        // Virus/zararli kod tekshiruvi — LibreOffice'ga (ishonchsiz kirish
        // ma'lumotini qayta ishlaydigan tashqi dastur) yuborishdan OLDIN,
        // xuddi convertHeicToJpeg'dagi kabi.
        clamAvScanService.scan(content, file.getOriginalFilename());

        String extension = extractExtension(file.getOriginalFilename(), ALLOWED_PPT_EXTENSIONS);
        if (extension.isEmpty()) {
            extension = (detectedType.contains("openxmlformats") || looksLikePptxZip) ? ".pptx" : ".ppt";
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("ppt-import-");
            Path inputFile = workDir.resolve("input" + extension);
            Files.write(inputFile, content);

            convertPptToPdf(inputFile, workDir);

            Path pdfFile = workDir.resolve("input.pdf");
            if (!Files.exists(pdfFile)) {
                throw new IllegalStateException("❌Taqdimotni PDF'ga o'girib bo'lmadi.");
            }

            splitPdfIntoSlideImages(pdfFile, workDir);

            List<Path> slideFiles;
            try (Stream<Path> paths = Files.list(workDir)) {
                slideFiles = paths
                        .filter(p -> p.getFileName().toString().startsWith("slide-")
                                && p.getFileName().toString().endsWith(".png"))
                        .sorted(Comparator.comparingInt(this::extractSlideNumber))
                        .toList();
            }

            if (slideFiles.isEmpty()) {
                throw new IllegalStateException("❌Taqdimotda hech qanday slayd topilmadi.");
            }

            Path targetDir = Path.of(uploadDir, "courses").toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String prefix = UUID.randomUUID().toString();
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < slideFiles.size(); i++) {
                String newFileName = prefix + "-slide-" + (i + 1) + ".png";
                Path targetFile = targetDir.resolve(newFileName).normalize();

                // Path traversal'dan himoya — store()dagi bilan bir xil tekshiruv.
                if (!targetFile.startsWith(targetDir)) {
                    throw new IllegalArgumentException("❌Noto'g'ri fayl nomi.");
                }

                Files.copy(slideFiles.get(i), targetFile);
                urls.add("/uploads/courses/" + newFileName);
            }

            return urls;
        } catch (IOException e) {
            log.error("PPT import qilishda xatolik", e);
            throw new IllegalStateException("❌Taqdimotni saqlashda xatolik.", e);
        } finally {
            deleteDirectoryQuietly(workDir);
        }
    }

    // ZIP fayl ICHIDA "ppt/presentation.xml" bor-yo'qligini tekshiradi —
    // .pptx (OOXML) paketining ANIQ, shubhasiz belgisi (Tika'ning umumiy
    // "application/zip" taxminidan farqli, storeCoursePptSlides'da
    // izohlangan haqiqiy production holat uchun).
    private boolean containsPptxPresentationXml(byte[] content) {
        try (java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("ppt/presentation.xml".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    // LibreOffice (headless) orqali .ppt/.pptx faylni PDF'ga o'giradi —
    // natija xuddi shu papkaga, kirish fayli bilan bir xil nom ostida
    // ("input.pdf") yoziladi (soffice'ning o'zi shunday nomlaydi).
    // "-env:UserInstallation" — HAR BIR chaqiruvga ALOHIDA, vaqtinchalik
    // profil papkasi beradi: aks holda soffice bir vaqtning o'zida bir
    // nechta so'rov (yoki oldingi jarayon tozalanmay qolgan bo'lsa) kelsa,
    // umumiy profilni qulflab, "boshqa nusxa allaqachon ishlamoqda" xatosi
    // bilan to'xtab qoladi.
    private void convertPptToPdf(Path inputFile, Path workDir) {
        Path profileDir = workDir.resolve("loprofile");
        try {
            Process process = new ProcessBuilder(
                    "soffice", "--headless", "--norestore",
                    "-env:UserInstallation=file://" + profileDir.toAbsolutePath(),
                    "--convert-to", "pdf", "--outdir", workDir.toString(), inputFile.toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("❌Taqdimotni PDF'ga o'girish vaqti tugadi.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("❌Taqdimotni PDF'ga o'girishda xatolik.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("❌LibreOffice orqali o'girishda xatolik.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("❌Taqdimotni o'girish to'xtatildi.", e);
        }
    }

    // PDF'ning har bir sahifasini alohida PNG rasmga ("slide-1.png",
    // "slide-2.png", ...) ajratadi — poppler-utils'ning "pdftoppm" buyrug'i.
    private void splitPdfIntoSlideImages(Path pdfFile, Path workDir) {
        try {
            Process process = new ProcessBuilder(
                    "pdftoppm", "-png", "-r", "120", pdfFile.toString(), workDir.resolve("slide").toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("❌Slaydlarni rasmga aylantirish vaqti tugadi.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("❌Slaydlarni rasmga aylantirishda xatolik.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("❌pdftoppm orqali o'girishda xatolik.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("❌Slaydlarga ajratish to'xtatildi.", e);
        }
    }

    // "slide-1.png" / "slide-01.png" (pdftoppm sahifalar soniga qarab
    // raqamni nolь bilan to'ldirishi mumkin) faylidan raqamni ajratib
    // oladi — slaydlar TO'G'RI tartibda (1,2,3,...) saralanishi uchun
    // (oddiy alifbo tartibida "slide-10.png" "slide-2.png"dan OLDIN
    // kelib qolardi).
    private int extractSlideNumber(Path path) {
        String name = path.getFileName().toString();
        String numberPart = name.substring("slide-".length(), name.length() - ".png".length());
        try {
            return Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Vaqtinchalik faylni o'chirib bo'lmadi: {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Vaqtinchalik papkani tozalashda xatolik: {}", dir, e);
        }
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

        // Windows'da .heic fayllar uchun MIME turi ko'pincha operatsion
        // tizim darajasida ro'yxatdan o'tmagan (faqat Apple qurilmalarida
        // "image/heic" to'g'ri aniqlanadi) — brauzer bunday holda generik
        // "application/octet-stream" yuboradi. Bu holatda darhol rad
        // etmasdan, pastdagi Tika (haqiqiy magic-byte) tekshiruviga
        // ishonamiz — u baribir yagona ISHONCHLI manba (shu sababdan
        // "client Content-Type header'iga ishonib bo'lmaydi" izohi bor).
        String contentType = file.getContentType();
        boolean declaredTypeUnknown = contentType == null || contentType.equalsIgnoreCase("application/octet-stream");
        if (!declaredTypeUnknown && !allowedContentTypes.contains(contentType.toLowerCase())) {
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

        // 2) Virus/zararli kod tekshiruvi (ClamAV yoqilgan bo'lsa) — HEIC/HEIF
        // konvertatsiyasidan OLDIN, hali xom (o'girishga yuborilishi mumkin
        // bo'lgan) baytlar ustida, chunki konvertatsiya vositasi ham
        // ishonchsiz kirish ma'lumotini qayta ishlaydi.
        clamAvScanService.scan(content, file.getOriginalFilename());

        // HEIC/HEIF -> JPEG: ko'pchilik brauzer HEIC'ni ko'rsata olmagani
        // uchun, saqlashdan oldin albatta JPEG'ga o'giramiz (kengaytma ham
        // shunga qarab ".jpg"ga almashtiriladi, aks holda extractExtension()
        // ham HEIC kengaytmasini saqlab qo'yardi-yu, lekin fayl ichi JPEG
        // bo'lardi — nomi bilan mazmuni mos kelmasdi).
        String extensionOverride = null;
        if (detectedType.equalsIgnoreCase("image/heic") || detectedType.equalsIgnoreCase("image/heif")) {
            content = convertHeicToJpeg(content);
            extensionOverride = ".jpg";
        }

        try {
            Path targetDir = Path.of(uploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(targetDir);

            String extension = extensionOverride != null
                    ? extensionOverride
                    : extractExtension(file.getOriginalFilename(), allowedExtensions);
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

    // HEIC/HEIF -> JPEG konvertatsiyasi — Java'ning o'zida HEIC dekoderi
    // yo'q (HEVC kodek litsenziyasi sababli), shu sabab tizimga o'rnatilgan
    // "heif-convert" (libheif-examples, Dockerfile'da o'rnatiladi) buyrug'i
    // orqali, alohida jarayon sifatida chaqiriladi.
    private byte[] convertHeicToJpeg(byte[] content) {
        Path tempInput = null;
        Path tempOutput = null;
        try {
            tempInput = Files.createTempFile("heic-in-", ".heic");
            tempOutput = Files.createTempFile("heic-out-", ".jpg");
            Files.write(tempInput, content);

            Process process = new ProcessBuilder("heif-convert", tempInput.toString(), tempOutput.toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("❌HEIC/HEIF rasmni o'girish vaqti tugadi.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("❌HEIC/HEIF rasmni JPEG'ga o'girishda xatolik.");
            }
            return Files.readAllBytes(tempOutput);
        } catch (IOException e) {
            log.error("HEIC->JPEG konvertatsiyasida xatolik", e);
            throw new IllegalStateException("❌HEIC/HEIF rasmni o'girishda xatolik.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("❌HEIC/HEIF rasmni o'girish to'xtatildi.", e);
        } finally {
            deleteQuietly(tempInput);
            deleteQuietly(tempOutput);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Vaqtinchalik faylni o'chirib bo'lmadi: {}", path, e);
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
