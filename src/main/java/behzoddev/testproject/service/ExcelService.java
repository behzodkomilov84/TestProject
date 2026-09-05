package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.answer.AnswerShortDto;
import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.export.ExportedFileDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
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

    // Foydalanuvchi so'rovi, 2026-09-05: "import qilishdagi test soni uchun
    // cheklovlarni olib tashla". Tekshirib chiqilgach — savollar SONIGA
    // hech qanday to'g'ridan-to'g'ri cheklov YO'Q edi (haqiqiy sabab —
    // ExcelService#importQuestions'dagi @Transactional/VARCHAR(255) bug
    // edi, allaqachon tuzatilgan). Yagona haqiqiy cheklov — shu fayl
    // HAJMI edi (10MB), Spring'ning o'zi qabul qiladigan (application.yaml
    // multipart.max-file-size=50MB) darajaga ko'tarildi — juda ko'p
    // savolli (masalan minglab qatorli) fayllar uchun ham yetarli bo'lsin.
    private static final long MAX_EXCEL_SIZE_BYTES = 50L * 1024 * 1024; // 50MB
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
    // Bo'lim/Fan miqyosidagi eksport uchun — "Mavzu" ustuni qo'shilgan
    // (bir nechta mavzu birga bo'lgani uchun, qaysi savol qaysi
    // mavzuga tegishli ekanini bilish uchun kerak).
    private static final String[] MULTI_TOPIC_EXPORT_HEADERS =
            {"Mavzu", "Question", "A", "B", "C", "D", "E", "Correct", "Comment (Faqat to'g'ri javob uchun)"};
    private static final String[] ANSWER_LETTERS = {"A", "B", "C", "D", "E"};

    private final QuestionService questionService;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final TopicSectionRepository topicSectionRepository;
    private final ScienceRepository scienceRepository;
    private final DataFormatter formatter = new DataFormatter();
    private final AnswerService answerService;
    private final Validation validation;
    private final ClamAvScanService clamAvScanService;
    private final Tika tika = new Tika();

    // "📥 Excel'ga eksport" — shu mavzudagi BARCHA faol savollarni import
    // shablonidagi ustun tartibida (Question/A/B/C/D/E/Correct/Comment)
    // .xlsx faylga yozadi. DIQQAT: sarlavha QATOR sifatida QO'SHILMAYDI —
    // importQuestions() 0-qatorni sarlavha, 1-qatordan boshlab ma'lumot
    // deb o'qiydi (round-trip: shu faylni qayta import qilish mumkin
    // bo'lishi kerak). Mavzu nomi shu sabab QATOR emas, VARAQ (sheet)
    // NOMI sifatida qo'yiladi — Excel'da fayl ochilganda pastdagi
    // tab'da ko'rinadi, import qatorlar tartibiga ta'sir qilmaydi.
    @Transactional(readOnly = true)
    public ExportedFileDto exportQuestions(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Mavzu topilmadi: " + topicId));
        List<Question> questions = questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(topicId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(topic.getName()));

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
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(q.getQuestionText());
                writeAnswerColumns(row, 1, q);
            }

            wb.write(out);
            return new ExportedFileDto(out.toByteArray(), ExportFilenameUtil.sanitize(topic.getName()));
        } catch (IOException e) {
            log.error("Excelga eksport qilishda xatolik", e);
            throw new RuntimeException("❌Excelga eksport qilishda xatolik", e);
        }
    }

    // Bo'lim/Fan miqyosida eksport — bir nechta mavzu savollarini BITTA
    // faylga yig'adi, har bir qatorda qaysi mavzuga tegishli ekani
    // ("Mavzu" ustuni, ENG BOSHIDA) ko'rsatiladi — bitta mavzulik
    // eksportdan farqli, bu holatda qaysi savol qaysi mavzuga tegishli
    // ekanini bilish MUHIM. DIQQAT: import shabloni bilan mos EMAS
    // (ustun soni/tartibi boshqacha, "Mavzu" ustuni qo'shilgan) — bu
    // eksport faqat KO'RISH/hisobot uchun, qayta import qilib bo'lmaydi.
    @Transactional(readOnly = true)
    public ExportedFileDto exportQuestionsForSection(Long sectionId) {
        TopicSection section = topicSectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Bo'lim topilmadi: " + sectionId));
        byte[] data = exportQuestionsForTopics("Bo'lim: " + section.getName(),
                topicRepository.findBySection_IdAndDeletedAtIsNullOrderByOrderIndexAsc(sectionId));
        return new ExportedFileDto(data, ExportFilenameUtil.sanitize(section.getName()));
    }

    @Transactional(readOnly = true)
    public ExportedFileDto exportQuestionsForScience(Long scienceId) {
        Science science = scienceRepository.findById(scienceId)
                .orElseThrow(() -> new RuntimeException("Fan topilmadi: " + scienceId));
        byte[] data = exportQuestionsForTopics("Fan: " + science.getName(),
                topicRepository.findByScience_IdAndDeletedAtIsNullOrderByOrderIndexAsc(scienceId));
        return new ExportedFileDto(data, ExportFilenameUtil.sanitize(science.getName()));
    }

    // Bu eksport allaqachon import bilan mos EMAS (yuqoridagi izohga
    // qarang), shu sabab bu yerda — WordService'dagi kabi — ustki
    // sarlavha (Bo'lim:/Fan: nomi) QATOR sifatida, birlashtirilgan
    // katakda qo'shiladi (sarlavha uchun 0-qator, jadval boshi 1-qator,
    // ma'lumot 2-qatordan boshlanadi).
    private byte[] exportQuestionsForTopics(String title, List<Topic> topics) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(title));

            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, MULTI_TOPIC_EXPORT_HEADERS.length - 1));

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(1);
            for (int i = 0; i < MULTI_TOPIC_EXPORT_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(MULTI_TOPIC_EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int[] columnWidthsChars = {35, 50, 25, 25, 25, 25, 25, 10, 40};
            for (int i = 0; i < columnWidthsChars.length; i++) {
                sheet.setColumnWidth(i, columnWidthsChars[i] * 256);
            }

            int rowIdx = 2;
            for (Topic topic : topics) {
                List<Question> questions = questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(topic.getId());
                for (Question q : questions) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(topic.getName());
                    row.createCell(1).setCellValue(q.getQuestionText());
                    writeAnswerColumns(row, 2, q);
                }
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Excelga eksport qilishda xatolik", e);
            throw new RuntimeException("❌Excelga eksport qilishda xatolik", e);
        }
    }

    // Bitta savolning A/B/C/D/E javob ustunlari + Correct + Comment
    // ustunlarini "startCol"dan boshlab yozadi (bitta mavzulik eksportda
    // startCol=1, ko'p mavzulik eksportda startCol=2 — "Mavzu"/"Question"
    // ustunlaridan keyin).
    private void writeAnswerColumns(Row row, int startCol, Question q) {
        List<Answer> answers = q.getAnswers() == null
                ? List.of()
                : q.getAnswers().stream()
                        .sorted(Comparator.comparing(Answer::getId))
                        .limit(5)
                        .toList();

        // Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi, 2026-09-05)
        // — ilgari faqat OXIRGI topilgan to'g'ri javob saqlanardi (har
        // safar ustidan yozilardi), qolganlari "yo'qolib" ketardi. Endi
        // BARCHA to'g'ri javoblar to'planadi — bitta bo'lsa import
        // shabloni bilan AYNAN bir xil natija ("A"), bir nechta bo'lsa
        // "A,B" (importQuestions#parseCorrectIndexes shu formatni o'qiy oladi).
        List<String> correctLetters = new ArrayList<>();
        List<String> correctComments = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            Answer a = answers.get(i);
            row.createCell(startCol + i).setCellValue(a.getAnswerText());
            if (Boolean.TRUE.equals(a.getIsTrue())) {
                correctLetters.add(ANSWER_LETTERS[i]);
                if (a.getCommentary() != null && !a.getCommentary().isBlank()) {
                    correctComments.add(a.getCommentary());
                }
            }
        }
        row.createCell(startCol + 5).setCellValue(String.join(",", correctLetters));
        row.createCell(startCol + 6).setCellValue(String.join(" ", correctComments));
    }

    // DIQQAT: bu metod ATAYIN @Transactional EMAS — har bir qator
    // QuestionService.save() ichida O'Z ALOHIDA (REQUIRES_NEW)
    // tranzaksiyasida saqlanadi, shu sabab bitta qatordagi xatolik
    // qolgan qatorlarning import bo'lishiga xalaqit bermaydi (pastdagi
    // izohga qarang).
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

            // Ko'p to'g'ri javobli savollar — 2-bosqich (foydalanuvchi
            // so'rovi, 2026-09-05: "umumiy shablon"). "Correct" ustunida
            // endi bitta harf ("A") YOKI bir nechtasi vergul/probel bilan
            // ("A,B" yoki "A B") bo'lishi mumkin — eski bitta harfli
            // fayllar O'ZGARISHSIZ ishlaydi (parseCorrectIndexes bitta
            // elementli ro'yxat qaytaradi).
            List<Integer> correctIndexes = parseCorrectIndexes(correct);

            String commentOfWrongAnswer = "Noto'g'ri javob";

            // Excel orqali import qilinganda rasm/video bo'lmaydi (barchasi null) —
            // ular faqat saytdagi "savol yaratish" formasi orqali qo'shiladi.
            List<AnswerShortDto> answerShortDtoList = new ArrayList<>();
            answerShortDtoList.add(new AnswerShortDto(a, correctIndexes.contains(0), correctIndexes.contains(0) ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(b, correctIndexes.contains(1), correctIndexes.contains(1) ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(c, correctIndexes.contains(2), correctIndexes.contains(2) ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(d, correctIndexes.contains(3), correctIndexes.contains(3) ? comment : commentOfWrongAnswer, null, null, null));
            answerShortDtoList.add(new AnswerShortDto(e, correctIndexes.contains(4), correctIndexes.contains(4) ? comment : commentOfWrongAnswer, null, null, null));

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
            throw new IllegalArgumentException("❌Fayl hajmi 50MB dan katta bo'lishi mumkin emas.");
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

    // Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi, 2026-09-05) —
    // "Correct" ustunida bitta harf ("A") YOKI bir nechtasi vergul/probel/
    // qiya chiziq bilan ajratilgan holda ("A,B" yoki "A B" yoki "A/B")
    // bo'lishi mumkin. Bitta harfli qatorlar bitta elementli ro'yxat
    // qaytaradi — import natijasi eski xatti-harakat bilan AYNAN bir xil.
    private List<Integer> parseCorrectIndexes(String correctSpec) {
        List<Integer> indexes = new ArrayList<>();
        for (String part : correctSpec.split("[,/\\s]+")) {
            if (part.isBlank()) continue;
            int idx = switch (part.trim().toUpperCase()) {
                case "A" -> 0;
                case "B" -> 1;
                case "C" -> 2;
                case "D" -> 3;
                case "E" -> 4;
                default -> throw new IllegalArgumentException(
                        "❌To'g'ri javob varianti faqat A/B/C/D/E dan biri (yoki bir nechtasi, vergul bilan ajratib — masalan \"A,B\") bo'lishi mumkin.");
            };
            if (!indexes.contains(idx)) indexes.add(idx);
        }
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException(
                    "❌To'g'ri javob varianti faqat A/B/C/D/E dan biri (yoki bir nechtasi, vergul bilan ajratib — masalan \"A,B\") bo'lishi mumkin.");
        }
        return indexes;
    }

}
