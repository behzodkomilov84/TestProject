package behzoddev.testproject.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Fayl yuklash — ikki bosqichli himoya: (1) Tika magic-byte tekshiruvi
 * (client Content-Type header'iga ishonilmaydi), (2) ClamAV. Real fayl
 * baytlari (PNG imzosi) bilan ishlaydi — soxta Content-Type header'ni
 * ushlab qolishni haqiqatan tekshiradi.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    // Haqiqiy PNG imzosi (Tika shu magic-byte orqali "image/png" deb aniqlaydi).
    private static final byte[] PNG_HEADER = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0, 'I', 'H', 'D', 'R'
    };

    @Mock
    private ClamAvScanService clamAvScanService;

    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(clamAvScanService);
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());
    }

    @Test
    void storeQuestionImage_validPng_savesFileUnderQuestionsSubdir() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", PNG_HEADER);

        String path = fileStorageService.storeQuestionImage(file);

        assertThat(path).startsWith("/uploads/questions/").endsWith(".png");
        Path saved = tempDir.resolve("questions").resolve(path.substring("/uploads/questions/".length()));
        assertThat(Files.exists(saved)).isTrue();
        verify(clamAvScanService).scan(any(byte[].class), anyString());
    }

    @Test
    void storeQuestionImage_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeQuestionImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tanlanmagan");
    }

    @Test
    void storeQuestionImage_tooLarge_throwsWithoutScanning() {
        byte[] tooLarge = new byte[6 * 1024 * 1024]; // 6MB > 5MB limit
        System.arraycopy(PNG_HEADER, 0, tooLarge, 0, PNG_HEADER.length);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", tooLarge);

        assertThatThrownBy(() -> fileStorageService.storeQuestionImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");

        verify(clamAvScanService, never()).scan(any(), any());
    }

    @Test
    void storeQuestionImage_declaredContentTypeNotAllowed_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "test.exe", "application/x-msdownload", PNG_HEADER);

        assertThatThrownBy(() -> fileStorageService.storeQuestionImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Faqat rasm fayllari");
    }

    @Test
    void storeQuestionImage_spoofedContentType_realBytesAreNotImage_throws() {
        // Client "image/png" deb da'vo qiladi, lekin haqiqiy baytlar oddiy matn —
        // Tika buni ushlab qolishi kerak (magic-byte tekshiruvi).
        byte[] plainTextBytes = "bu shunchaki matn fayli".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", plainTextBytes);

        assertThatThrownBy(() -> fileStorageService.storeQuestionImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Faqat rasm fayllari");

        verify(clamAvScanService, never()).scan(any(), any());
    }

    @Test
    void storeQuestionImage_clamAvRejectsFile_throwsAndDoesNotWriteToDisk() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "virus.png", "image/png", PNG_HEADER);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("❌ Fayl zararli dastur (virus) sifatida aniqlandi"))
                .when(clamAvScanService).scan(any(), anyString());

        assertThatThrownBy(() -> fileStorageService.storeQuestionImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zararli");

        // Hech qanday fayl yozilmagan bo'lishi kerak.
        Path questionsDir = tempDir.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (var stream = Files.list(questionsDir)) {
                assertThat(stream.count()).isZero();
            }
        }
    }

    @Test
    void storeCommentaryImage_unrecognizedExtensionInOriginalName_stripsExtensionButStillSaves() {
        // Ruxsat etilmagan kengaytma (.dat) bo'lsa, extractExtension "" qaytaradi
        // — fayl baribir saqlanadi (Content-Type/Tika tekshiruvidan o'tgani uchun),
        // faqat kengaytmasiz.
        MockMultipartFile file = new MockMultipartFile("file", "image.dat", "image/png", PNG_HEADER);

        String path = fileStorageService.storeCommentaryImage(file);

        assertThat(path).startsWith("/uploads/commentary/");
        assertThat(path).doesNotContain(".dat");
    }

    @Test
    void differentUploadTypes_goToDifferentSubdirectories() {
        MockMultipartFile courseImage = new MockMultipartFile("file", "cover.png", "image/png", PNG_HEADER);

        String path = fileStorageService.storeCourseCoverImage(courseImage);

        assertThat(path).startsWith("/uploads/courses/");
    }

    @Test
    void storeCourseSectionImage_validPng_savesFileUnderCoursesSubdir() {
        // Bo'lim matni tahrirlagichidagi "🖼 Rasm qo'shish" tugmasi shu
        // metodni chaqiradi — muqova rasmi bilan bir xil "courses" papkasi
        // va tekshiruvlardan foydalanadi.
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", PNG_HEADER);

        String path = fileStorageService.storeCourseSectionImage(file);

        assertThat(path).startsWith("/uploads/courses/").endsWith(".png");
        verify(clamAvScanService).scan(any(byte[].class), anyString());
    }
}
