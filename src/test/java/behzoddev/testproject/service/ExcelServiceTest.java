package behzoddev.testproject.service;

import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.validation.Validation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Excel orqali savol import qilish — .xlsx faylni Apache POI bilan
 * REAL yaratib (magic-byte/Tika, ClamAV, parsing), qatorlar bo'yicha
 * xatolik izolyatsiyasini tekshiradi (bitta qatordagi xato boshqa
 * qatorlarni import qilishga to'sqinlik qilmasligi kerak).
 */
@ExtendWith(MockitoExtension.class)
class ExcelServiceTest {

    @Mock
    private QuestionService questionService;
    @Mock
    private AnswerService excelAnswerService; // ExcelService'ning o'z isUnique() tekshiruvi uchun
    @Mock
    private AnswerService validationAnswerService; // faqat Validation ichida ishlatiladi (bu oqimda chaqirilmaydi)
    @Mock
    private ClamAvScanService clamAvScanService;

    private ExcelService excelService;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(validationAnswerService);
        excelService = new ExcelService(questionService, excelAnswerService, validation, clamAvScanService);
        // Fayl-darajasidagi validatsiya testlari (bo'sh/katta/noto'g'ri kengaytma)
        // qatorlarni umuman o'qishga yetmaydi — shu stublar ular uchun keraksiz
        // bo'lgani uchun lenient qilingan.
        org.mockito.Mockito.lenient().when(excelAnswerService.isUnique(any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(questionService.getQuestionSaveDtoByTopicId(anyLong())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(questionService.isQuestionWithAnswersExists(anyList(), any(QuestionSaveDto.class)))
                .thenReturn(false);
    }

    private byte[] buildWorkbook(String[]... rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Savollar");
            sheet.createRow(0); // sarlavha qatori (0-indeks) — importda o'tkazib yuboriladi

            int rowNum = 1;
            for (String[] cells : rows) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < cells.length; i++) {
                    row.createCell(i).setCellValue(cells[i]);
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private MockMultipartFile excelFile(byte[] content) {
        return new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private static <T> List<T> anyList() {
        return org.mockito.ArgumentMatchers.anyList();
    }

    // ===== muvaffaqiyatli import =====

    @Test
    void importQuestions_validRow_importsSuccessfully() throws IOException {
        byte[] content = buildWorkbook(new String[]{"2+2 nechiga teng?", "3", "4", "5", "6", "7", "B", "Yig'indi"});

        ImportResultDto result = excelService.importQuestions(excelFile(content), 1L);

        assertThat(result.success()).isTrue();
        assertThat(result.imported()).isEqualTo(1L);
        assertThat(result.errors()).isEmpty();
        verify(questionService).save(any(QuestionSaveDto.class));
    }

    @Test
    void importQuestions_correctAnswerMarkedTrueInDto() throws IOException {
        byte[] content = buildWorkbook(new String[]{"Savol", "birinchi", "ikkinchi", "uchinchi", "to'rtinchi", "beshinchi", "C", "Izoh"});

        org.mockito.ArgumentCaptor<QuestionSaveDto> captor = org.mockito.ArgumentCaptor.forClass(QuestionSaveDto.class);
        excelService.importQuestions(excelFile(content), 1L);

        verify(questionService).save(captor.capture());
        assertThat(captor.getValue().answers().get(2).answerText()).isEqualTo("uchinchi");
        assertThat(captor.getValue().answers().get(2).isTrue()).isTrue();
        assertThat(captor.getValue().answers().get(0).isTrue()).isFalse();
    }

    @Test
    void importQuestions_multipleRows_allImported() throws IOException {
        byte[] content = buildWorkbook(new String[]{"Savol 1", "a", "b", "c", "d", "e", "A", "izoh1"},
                new String[]{"Savol 2", "a", "b", "c", "d", "e", "B", "izoh2"});

        ImportResultDto result = excelService.importQuestions(excelFile(content), 1L);

        assertThat(result.success()).isTrue();
        assertThat(result.imported()).isEqualTo(2L);
        verify(questionService, times(2)).save(any());
    }

    // ===== qator darajasidagi xatolar (izolyatsiya) =====

    @Test
    void importQuestions_invalidCorrectLetter_recordsRowErrorWithoutStoppingOtherRows() throws IOException {
        byte[] content = buildWorkbook(new String[]{"Yaroqsiz savol", "a", "b", "c", "d", "e", "Z", "izoh"},
                new String[]{"Yaroqli savol", "a", "b", "c", "d", "e", "A", "izoh"});

        ImportResultDto result = excelService.importQuestions(excelFile(content), 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.imported()).isEqualTo(1L); // faqat 2-qator
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Row 2");
    }

    @Test
    void importQuestions_duplicateAnswersInRow_recordsError() throws IOException {
        when(excelAnswerService.isUnique(any())).thenReturn(false);
        byte[] content = buildWorkbook(new String[]{"Savol", "a", "a", "c", "d", "e", "A", "izoh"});

        ImportResultDto result = excelService.importQuestions(excelFile(content), 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.imported()).isZero();
        assertThat(result.errors().get(0)).contains("bir xil bo'lishi mumkin emas");
        verify(questionService, never()).save(any());
    }

    @Test
    void importQuestions_duplicateQuestionAlreadyInDb_recordsError() throws IOException {
        when(questionService.isQuestionWithAnswersExists(anyList(), any(QuestionSaveDto.class))).thenReturn(true);
        byte[] content = buildWorkbook(new String[]{"Mavjud savol", "a", "b", "c", "d", "e", "A", "izoh"});

        ImportResultDto result = excelService.importQuestions(excelFile(content), 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0)).contains("allaqachon bazada mavjud");
        verify(questionService, never()).save(any());
    }

    @Test
    void importQuestions_blankQuestionText_recordsErrorViaRealValidation() throws IOException {
        byte[] content = buildWorkbook(new String[]{"", "a", "b", "c", "d", "e", "A", "izoh"});

        ImportResultDto result = excelService.importQuestions(excelFile(content), 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.imported()).isZero();
        verify(questionService, never()).save(any());
    }

    // ===== fayl darajasidagi validatsiya =====

    @Test
    void importQuestions_emptyFile_returnsFailureWithoutParsing() {
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        ImportResultDto result = excelService.importQuestions(file, 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0)).contains("tanlanmagan");
    }

    @Test
    void importQuestions_tooLarge_returnsFailureWithoutScanning() {
        byte[] tooLarge = new byte[11 * 1024 * 1024]; // 11MB > 10MB limit
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", tooLarge);

        ImportResultDto result = excelService.importQuestions(file, 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0)).contains("10MB");
        verify(clamAvScanService, never()).scan(any(), any());
    }

    @Test
    void importQuestions_wrongExtension_returnsFailure() throws IOException {
        byte[] content = buildWorkbook(new String[]{"Savol", "a", "b", "c", "d", "e", "A", "izoh"});
        MockMultipartFile file = new MockMultipartFile("file", "questions.txt", "text/plain", content);

        ImportResultDto result = excelService.importQuestions(file, 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0)).contains(".xlsx");
    }

    @Test
    void importQuestions_spoofedExtension_realContentNotExcel_returnsFailure() {
        byte[] plainTextBytes = "bu excel fayl emas".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", plainTextBytes);

        ImportResultDto result = excelService.importQuestions(file, 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0)).contains("haqiqiy Excel fayli emas");
        verify(clamAvScanService, never()).scan(any(), any());
    }

    @Test
    void importQuestions_clamAvRejects_returnsFailureWithoutParsingRows() throws IOException {
        byte[] content = buildWorkbook(new String[]{"Savol", "a", "b", "c", "d", "e", "A", "izoh"});
        org.mockito.Mockito.doThrow(new IllegalArgumentException("❌ Fayl zararli dastur (virus) sifatida aniqlandi"))
                .when(clamAvScanService).scan(any(), any());

        ImportResultDto result = excelService.importQuestions(excelFile(content), 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0)).contains("zararli");
        verify(questionService, never()).save(any());
    }
}
