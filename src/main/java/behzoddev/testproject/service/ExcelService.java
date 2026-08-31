package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dto.answer.AnswerShortDto;
import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.tika.Tika;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    private static final long MAX_EXCEL_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXCEL_EXTENSIONS = List.of(".xlsx", ".xls");
    private static final Set<String> ALLOWED_EXCEL_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "application/vnd.ms-excel", // .xls
            "application/x-tika-msoffice" // Tika eski .xls (OLE2) uchun ba'zan shu umumiy turni qaytaradi
    );

    // Import shablonidagi (template_For_Import.xlsx) sarlavhalar bilan
    // AYNAN bir xil — shu sabab eksport qilingan faylni qayta import
    // qilib bo'ladi (round-trip).
    private static final String[] EXPORT_HEADERS =
            {"Question", "A", "B", "C", "D", "E", "Correct", "Comment (Faqat to'g'ri javob uchun)"};
    private static final String[] ANSWER_LETTERS = {"A", "B", "C", "D", "E"};

    private final QuestionService questionService;
    private final QuestionRepository questionRepository;
    private final DataFormatter formatter = new DataFormatter();
    private final AnswerService answerService;
    private final Validation validation;
    private final ClamAvScanService clamAvScanService;
    private final Tika tika = new Tika();

    // "📥 Excel'ga eksport" — shu mavzudagi BARCHA faol savollarni import
    // shablonidagi ustun tartibida (Question/A/B/C/D/E/Correct/Comment)
    // .xlsx faylga yozadi.
    @Transactional(readOnly = true)
    public byte[] exportQuestions(Long topicId) {
        List<Question> questions = questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(topicId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Savollar");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sarlavha matni asosida taxminiy kenglik — autoSizeColumn()
            // AWT shrift kutubxonasiga tayanadi (serverda muammo bo'lishi
            // mumkin), shu sabab ataylab ishlatilmagan.
            int[] columnWidthsChars = {50, 25, 25, 25, 25, 25, 10, 40};
            for (int i = 0; i < columnWidthsChars.length; i++) {
                sheet.setColumnWidth(i, columnWidthsChars[i] * 256);
            }

            int rowIdx = 1;
            for (Question q : questions) {
                List<Answer> answers = q.getAnswers() == null
                        ? List.of()
                        : q.getAnswers().stream()
                                .sorted(Comparator.comparing(Answer::getId))
                                .limit(5)
                                .toList();

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(q.getQuestionText());

                String correctLetter = "";
                String correctComment = "";
                for (int i = 0; i < answers.size(); i++) {
                    Answer a = answers.get(i);
                    row.createCell(i + 1).setCellValue(a.getAnswerText());
                    if (Boolean.TRUE.equals(a.getIsTrue())) {
                        correctLetter = ANSWER_LETTERS[i];
                        correctComment = a.getCommentary() != null ? a.getCommentary() : "";
                    }
                }
                row.createCell(6).setCellValue(correctLetter);
                row.createCell(7).setCellValue(correctComment);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Excelga eksport qilishda xatolik", e);
            throw new RuntimeException("❌Excelga eksport qilishda xatolik", e);
        }
    }

    @Transactional
    public ImportResultDto importQuestions(MultipartFile file, Long topicId) {

        byte[] content;
        try {
            content = validateAndReadExcelFile(file);
        } catch (IllegalArgumentException e) {
            // Frontend (test-form.js) javobni to'g'ridan-to'g'ri ImportResultDto
            // sifatida o'qiydi (res.ok'ni tekshirmaydi) — shuning uchun
            // validatsiya xatoligi ham shu shaklda qaytarilishi shart.
            return new ImportResultDto(false, 0L, List.of(e.getMessage()));
        }

        List<String> errors = new ArrayList<>();
        Long imported = 0L;

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {

            Sheet sheet = wb.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);
                if (row == null) continue;

                imported = getValuesFromCellAndSaveToDataBase(topicId, row, imported, errors, i);
            }

        } catch (Exception e) {
            return new ImportResultDto(false, 0L, List.of("Invalid Excel file"));
        }

        if (!errors.isEmpty()) {
            return new ImportResultDto(false, imported, errors);
        }

        return new ImportResultDto(true, imported, List.of());
    }

    private @Nullable Long getValuesFromCellAndSaveToDataBase(Long topicId, Row row, Long imported, List<String> errors, int i) {
        try {
            String qText = cell(row, 0);
            String a = cell(row, 1);
            String b = cell(row, 2);
            String c = cell(row, 3);
            String d = cell(row, 4);
            String e = cell(row, 5);
            String correct = cell(row, 6);
            String comment = cell(row, 7);

            validation.textFieldMustNotBeEmpty(qText);
            validation.textFieldMustNotBeEmpty(a);
            validation.textFieldMustNotBeEmpty(b);
            validation.textFieldMustNotBeEmpty(c);
            validation.textFieldMustNotBeEmpty(d);
            validation.textFieldMustNotBeEmpty(e);
            validation.textFieldMustNotBeEmpty(correct);
            validation.textFieldMustNotBeEmpty(comment);

            int correctIndex = parseCorrect(correct);

            String commentOfWrongAnswer = "Noto'g'ri javob";

            // Excel orqali import qilinganda rasm/video bo'lmaydi (barchasi null) —
            // ular faqat saytdagi "savol yaratish" formasi orqali qo'shiladi.
            List<AnswerShortDto> answerShortDtoList = new ArrayList<>();
            answerShortDtoList.add(new AnswerShortDto(a, correctIndex == 0, correctIndex == 0 ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(b, correctIndex == 1, correctIndex == 1 ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(c, correctIndex == 2, correctIndex == 2 ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(d, correctIndex == 3, correctIndex == 3 ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(e, correctIndex == 4, correctIndex == 4 ? comment : commentOfWrongAnswer, null, null, null));

            List<String> answersText = List.of(a, b, c, d, e);

            boolean isUnique = answerService.isUnique(answersText); //Javoblarni bir xil masligini tekshiradi.

            if (!isUnique) {
                throw new IllegalArgumentException("❌Javoblar bir xil bo'lishi mumkin emas.");
            }

            QuestionSaveDto newQuestion = QuestionSaveDto.builder()
                    .questionText(qText)
                    .topicId(topicId)
                    .answers(answerShortDtoList)
                    .build();

            //Yangi testni DB da bor-yo'qligini tekshirish
            List<QuestionSaveDto> existingQuestions = questionService.getQuestionSaveDtoByTopicId(topicId);
            boolean questionWithAnswersExists = questionService.isQuestionWithAnswersExists(existingQuestions, newQuestion);

            if (questionWithAnswersExists) {
                throw new IllegalArgumentException("Bu test ayni shu javoblar bilan allaqachon bazada mavjud.");
            }

            questionService.save(newQuestion);

            imported++;

        } catch (Exception e) {
            errors.add("Row " + (i + 1) + ": " + e.getMessage());
        }
        return imported;
    }

    // Excel fayl haqiqatan ham yaroqli Excel fayli ekanini (kengaytma +
    // magic-byte) va zararli kod bo'lmasligini (ClamAV) tekshiradi —
    // FileStorageService'dagi rasm/video tekshiruvi bilan bir xil g'oya:
    // client yuborgan Content-Type header'iga ishonilmaydi.
    private byte[] validateAndReadExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("❌Fayl tanlanmagan.");
        }

        if (file.getSize() > MAX_EXCEL_SIZE_BYTES) {
            throw new IllegalArgumentException("❌Fayl hajmi 10MB dan katta bo'lishi mumkin emas.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXCEL_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("❌Faqat .xlsx yoki .xls formatidagi fayllar qabul qilinadi.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("Excel faylni o'qishda xatolik", e);
            throw new IllegalArgumentException("❌Faylni o'qib bo'lmadi.");
        }

        // Fayl nomi "hint" sifatida beriladi — bu OOXML ichidagi xlsx/docx/pptx
        // farqini aniqroq ajratadi, lekin haqiqiy magic-byte tekshiruvini
        // yengib bo'lmaydi (masalan .exe/.php fayl .xlsx deb nomlansa ham,
        // aniq turi bilan — application/x-msdownload va h.k. — ochiladi).
        String detectedType = tika.detect(content, originalFilename);
        if (!ALLOWED_EXCEL_TYPES.contains(detectedType)) {
            log.warn("Excel fayl turi mos kelmadi: fayl='{}', aniqlangan tur='{}'", originalFilename, detectedType);
            throw new IllegalArgumentException("❌Fayl haqiqiy Excel fayli emas (turi mos kelmadi).");
        }

        // Virus/zararli kod tekshiruvi (ClamAV yoqilgan bo'lsa).
        clamAvScanService.scan(content, originalFilename);

        return content;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex).toLowerCase();
    }

    private String cell(Row row, int i) {
        if (row.getCell(i) == null) return "";
        return formatter.formatCellValue(row.getCell(i)).trim();
    }

    private int parseCorrect(String c) {
        return switch (c.toUpperCase()) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> 2;
            case "D" -> 3;
            case "E" -> 4;
            default -> throw new IllegalArgumentException("❌To'g'ri javob varianti faqat A/B/C/D/E dan biri bo'lishi mumkin.");
        };
    }

}
