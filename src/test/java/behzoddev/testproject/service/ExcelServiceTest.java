package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
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
    private QuestionRepository questionRepository; // faqat exportQuestions() uchun (bu oqimda chaqirilmaydi)
    @Mock
    private behzoddev.testproject.dao.TopicRepository topicRepository; // faqat bo'lim/fan eksporti uchun (bu oqimda chaqirilmaydi)
    @Mock
    private behzoddev.testproject.dao.TopicSectionRepository topicSectionRepository; // faqat bo'lim eksporti uchun (bu oqimda chaqirilmaydi)
    @Mock
    private behzoddev.testproject.dao.ScienceRepository scienceRepository; // faqat fan eksporti uchun (bu oqimda chaqirilmaydi)
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
        excelService = new ExcelService(questionService, questionRepository, topicRepository, topicSectionRepository, scienceRepository, excelAnswerService, validation, clamAvScanService);
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

    // Ko'p to'g'ri javobli savollar — 2-bosqich (foydalanuvchi so'rovi,
    // 2026-09-05: "javoblarining bir nechtasi to'g'ri" testlar). "Correct"
    // ustunida vergul bilan ajratilgan bir nechta harf ("B,D") — HAR
    // IKKALASI ham isTrue=true bo'lib belgilanishi, qolganlari esa
    // false bo'lishi kerak.
    @Test
    void importQuestions_multipleCorrectAnswers_marksAllListedTrueInDto() throws IOException {
        byte[] content = buildWorkbook(new String[]{"Savol", "birinchi", "ikkinchi", "uchinchi", "to'rtinchi", "beshinchi", "B,D", "Izoh"});

        org.mockito.ArgumentCaptor<QuestionSaveDto> captor = org.mockito.ArgumentCaptor.forClass(QuestionSaveDto.class);
        excelService.importQuestions(excelFile(content), 1L);

        verify(questionService).save(captor.capture());
        assertThat(captor.getValue().answers().get(1).isTrue()).isTrue();  // B
        assertThat(captor.getValue().answers().get(3).isTrue()).isTrue();  // D
        assertThat(captor.getValue().answers().get(0).isTrue()).isFalse();
        assertThat(captor.getValue().answers().get(2).isTrue()).isFalse();
        assertThat(captor.getValue().answers().get(4).isTrue()).isFalse();
    }

    // Probel bilan ajratilgan variant ("A B") ham qabul qilinishi kerak —
    // vergul yagona ruxsat etilgan ajratuvchi emas.
    @Test
    void importQuestions_multipleCorrectAnswersSpaceSeparated_marksAllListedTrue() throws IOException {
        byte[] content = buildWorkbook(new String[]{"Savol", "a", "b", "c", "d", "e", "A E", "Izoh"});

        org.mockito.ArgumentCaptor<QuestionSaveDto> captor = org.mockito.ArgumentCaptor.forClass(QuestionSaveDto.class);
        excelService.importQuestions(excelFile(content), 1L);

        verify(questionService).save(captor.capture());
        assertThat(captor.getValue().answers().get(0).isTrue()).isTrue();  // A
        assertThat(captor.getValue().answers().get(4).isTrue()).isTrue();  // E
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
        byte[] tooLarge = new byte[51 * 1024 * 1024]; // 51MB > 50MB limit
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", tooLarge);

        ImportResultDto result = excelService.importQuestions(file, 1L);

        assertThat(result.success()).isFalse();
        assertThat(result.errors().get(0)).contains("50MB");
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

    // ===== exportQuestions =====

    @Test
    void exportQuestions_writesHeaderAndRowsMatchingImportFormat() throws IOException {
        Answer a = Answer.builder().id(1L).answerText("Toshkent").isTrue(false).build();
        Answer b = Answer.builder().id(2L).answerText("Samarqand").isTrue(true).commentary("To'g'ri, chunki...").build();
        Question q = Question.builder().id(100L).questionText("Qaysi shahar?").answers(List.of(a, b)).build();
        behzoddev.testproject.entity.Topic topic = behzoddev.testproject.entity.Topic.builder().id(5L).name("Shaharlar").build();

        when(topicRepository.findById(5L)).thenReturn(java.util.Optional.of(topic));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(5L)).thenReturn(List.of(q));

        behzoddev.testproject.dto.export.ExportedFileDto result = excelService.exportQuestions(5L);
        assertThat(result.filenameBase()).isEqualTo("Shaharlar");

        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.data()))) {
            Sheet sheet = wb.getSheetAt(0);
            // Mavzu nomi VARAQ (sheet) nomi sifatida — 0-qator hamon
            // sarlavha, import round-trip buzilmasin deb.
            assertThat(wb.getSheetName(0)).isEqualTo("Shaharlar");
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Question");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Correct");

            Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Qaysi shahar?");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("Toshkent");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("Samarqand");
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("B"); // 2-javob (indeks 1) to'g'ri
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("To'g'ri, chunki...");
        }
    }

    // Ko'p to'g'ri javobli savol eksporti — ilgari faqat OXIRGI topilgan
    // to'g'ri javob saqlanardi (qolganlari yo'qolib ketardi). Endi
    // "Correct" ustunida BARCHASI vergul bilan ("A,C") chiqishi kerak —
    // import bilan round-trip (ExcelService#parseCorrectIndexes shu
    // formatni o'qiy oladi).
    @Test
    void exportQuestions_multipleCorrectAnswers_joinsLettersAndCommentsWithComma() throws IOException {
        Answer a = Answer.builder().id(1L).answerText("Birinchi").isTrue(true).commentary("izoh1").build();
        Answer b = Answer.builder().id(2L).answerText("Ikkinchi").isTrue(false).build();
        Answer c = Answer.builder().id(3L).answerText("Uchinchi").isTrue(true).commentary("izoh2").build();
        Question q = Question.builder().id(100L).questionText("Savol").answers(List.of(a, b, c)).build();
        behzoddev.testproject.entity.Topic topic = behzoddev.testproject.entity.Topic.builder().id(5L).name("Mavzu").build();

        when(topicRepository.findById(5L)).thenReturn(java.util.Optional.of(topic));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(5L)).thenReturn(List.of(q));

        behzoddev.testproject.dto.export.ExportedFileDto result = excelService.exportQuestions(5L);

        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.data()))) {
            Row row = wb.getSheetAt(0).getRow(1);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("A,C");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("izoh1 izoh2");
        }
    }

    // ===== exportQuestionsForSection / exportQuestionsForScience =====

    @Test
    void exportQuestionsForSection_multipleTopics_addsTopicColumnAndShiftsAnswers() throws IOException {
        behzoddev.testproject.entity.Topic topic1 = behzoddev.testproject.entity.Topic.builder().id(10L).name("1-mavzu").build();
        behzoddev.testproject.entity.Topic topic2 = behzoddev.testproject.entity.Topic.builder().id(20L).name("2-mavzu").build();

        Answer a1 = Answer.builder().id(1L).answerText("Ha").isTrue(true).commentary("izoh1").build();
        Question q1 = Question.builder().id(1L).questionText("1-savol").answers(List.of(a1)).build();

        Answer a2 = Answer.builder().id(2L).answerText("Yo'q").isTrue(true).commentary("izoh2").build();
        Question q2 = Question.builder().id(2L).questionText("2-savol").answers(List.of(a2)).build();

        behzoddev.testproject.entity.TopicSection section =
                behzoddev.testproject.entity.TopicSection.builder().id(7L).name("Anatomiya").build();

        when(topicSectionRepository.findById(7L)).thenReturn(java.util.Optional.of(section));
        when(topicRepository.findBySection_IdAndDeletedAtIsNullOrderByOrderIndexAsc(7L))
                .thenReturn(List.of(topic1, topic2));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(10L)).thenReturn(List.of(q1));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(20L)).thenReturn(List.of(q2));

        behzoddev.testproject.dto.export.ExportedFileDto result = excelService.exportQuestionsForSection(7L);
        assertThat(result.filenameBase()).isEqualTo("Anatomiya");

        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.data()))) {
            Sheet sheet = wb.getSheetAt(0);
            // 0-qator — ustki sarlavha ("Bo'lim: Anatomiya", birlashtirilgan
            // katak), 1-qator — jadval boshi, 2-qatordan ma'lumot.
            Row titleRow = sheet.getRow(0);
            assertThat(titleRow.getCell(0).getStringCellValue()).isEqualTo("Bo'lim: Anatomiya");

            Row header = sheet.getRow(1);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Mavzu");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Question");

            Row row1 = sheet.getRow(2);
            assertThat(row1.getCell(0).getStringCellValue()).isEqualTo("1-mavzu");
            assertThat(row1.getCell(1).getStringCellValue()).isEqualTo("1-savol");
            assertThat(row1.getCell(2).getStringCellValue()).isEqualTo("Ha");
            assertThat(row1.getCell(7).getStringCellValue()).isEqualTo("A");

            Row row2 = sheet.getRow(3);
            assertThat(row2.getCell(0).getStringCellValue()).isEqualTo("2-mavzu");
            assertThat(row2.getCell(1).getStringCellValue()).isEqualTo("2-savol");
        }
    }
}
