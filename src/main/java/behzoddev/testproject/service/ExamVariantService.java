package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.export.ExportedFileDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// "🎲 Variantlar yaratish" — bir nechta O'QUVCHI uchun BIR XIL sondagi
// imtihon variantlarini bir yo'la yaratadi (masalan 30 talaba, har
// biriga 50 tadan savol). Ikki rejim bor:
//   - sameQuestions=false (default, "har biriga boshqa-boshqa savollar"):
//     har bir nusxa o'zining HAR XIL tasodifiy savol to'plamiga ega.
//   - sameQuestions=true ("barchasiga bir xil savollar"): savollar
//     TANLOVI faqat BIR MARTA qilinadi (barcha nusxa uchun bir xil —
//     baholash adolatli bo'lishi uchun), har bir nusxada esa faqat
//     savollar TARTIBI (va shuffleAnswers yoqilgan bo'lsa javoblar
//     tartibi ham) alohida aralashtiriladi — ko'chirib javob berishni
//     qiyinlashtirish uchun.
// Ikkala rejimda ham savollar tanlovi barcha MAVZULAR bo'yicha TENG
// taqsimlanadi (savollar soniga QARAB EMAS) — mavzuda savol yetmasa,
// yetmagan qism boshqa mavzularga (ularda ham joy bo'lsa) teng bo'lib
// qayta taqsimlanadi (allocateEqually()).
//
// Natija — BITTA .zip fayl: har biri alohida .docx bo'lgan
// "Variant_NN.docx" fayllar + "Javoblar_kaliti.xlsx" (o'qituvchi uchun,
// har bir variant/savol raqami bo'yicha to'g'ri javob harfi).
//
// Savol/javobga biriktirilgan rasm ham hujjatga qo'shiladi (DocxImageUtil) —
// video EMAS (Word .docx formatida video ko'rsatib bo'lmaydi, shu sabab
// har doim o'tkazib yuboriladi).
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamVariantService {

    private static final String[] ANSWER_LETTERS = {"A", "B", "C", "D", "E"};

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final TopicSectionRepository topicSectionRepository;
    private final ScienceRepository scienceRepository;

    // Savol/javobga biriktirilgan rasmni diskdan o'qish uchun (DocxImageUtil) —
    // FileStorageService bilan bir xil manba (application.yaml: app.upload.dir).
    @Value("${app.upload.dir}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public ExportedFileDto generateVariantsForTopic(Long topicId, int variantCount, int perVariant, boolean shuffleAnswers, boolean sameQuestions) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Mavzu topilmadi: " + topicId));
        byte[] data = generate("Mavzu: " + topic.getName(), List.of(topic), variantCount, perVariant, shuffleAnswers, sameQuestions);
        return new ExportedFileDto(data, "variantlar_" + ExportFilenameUtil.sanitize(topic.getName()));
    }

    @Transactional(readOnly = true)
    public ExportedFileDto generateVariantsForSection(Long sectionId, int variantCount, int perVariant, boolean shuffleAnswers, boolean sameQuestions) {
        TopicSection section = topicSectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Bo'lim topilmadi: " + sectionId));
        List<Topic> topics = topicRepository.findBySection_IdAndDeletedAtIsNullOrderByOrderIndexAsc(sectionId);
        byte[] data = generate("Bo'lim: " + section.getName(), topics, variantCount, perVariant, shuffleAnswers, sameQuestions);
        return new ExportedFileDto(data, "variantlar_bolim_" + ExportFilenameUtil.sanitize(section.getName()));
    }

    @Transactional(readOnly = true)
    public ExportedFileDto generateVariantsForScience(Long scienceId, int variantCount, int perVariant, boolean shuffleAnswers, boolean sameQuestions) {
        Science science = scienceRepository.findById(scienceId)
                .orElseThrow(() -> new RuntimeException("Fan topilmadi: " + scienceId));
        List<Topic> topics = topicRepository.findByScience_IdAndDeletedAtIsNullOrderByOrderIndexAsc(scienceId);
        byte[] data = generate("Fan: " + science.getName(), topics, variantCount, perVariant, shuffleAnswers, sameQuestions);
        return new ExportedFileDto(data, "variantlar_fan_" + ExportFilenameUtil.sanitize(science.getName()));
    }

    private byte[] generate(String scopeTitle, List<Topic> topics, int variantCount, int perVariant, boolean shuffleAnswers, boolean sameQuestions) {
        if (variantCount < 1) {
            throw new RuntimeException("❌ Variantlar soni kamida 1 bo'lishi kerak");
        }
        if (perVariant < 1) {
            throw new RuntimeException("❌ Har bir variantdagi savollar soni kamida 1 bo'lishi kerak");
        }

        Map<Long, List<Question>> questionsByTopic = new LinkedHashMap<>();
        for (Topic t : topics) {
            questionsByTopic.put(t.getId(), questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(t.getId()));
        }

        Random random = new Random();
        Map<Long, Integer> allocation = allocateEqually(topics, questionsByTopic, perVariant, random);

        // sameQuestions=true bo'lsa — savollar TANLOVI shu yerda BIR MARTA
        // qilinadi va pastda HAR BIR nusxa uchun shu ro'yxatning faqat
        // TARTIBI aralashtiriladi (tarkibi emas).
        List<Question> sharedSelection = sameQuestions
                ? pickFromTopics(topics, questionsByTopic, allocation, random)
                : null;

        int digits = String.valueOf(variantCount).length();
        List<Map<Integer, String>> answerKeys = new ArrayList<>();

        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
            for (int v = 1; v <= variantCount; v++) {
                List<Question> selected = sameQuestions
                        ? new ArrayList<>(sharedSelection)
                        : pickFromTopics(topics, questionsByTopic, allocation, random);
                Collections.shuffle(selected, random);

                Map<Integer, String> variantKey = new LinkedHashMap<>();
                byte[] docBytes = buildVariantDocument(scopeTitle, v, selected, shuffleAnswers, random, variantKey);
                answerKeys.add(variantKey);

                String entryName = "Variant_" + String.format("%0" + digits + "d", v) + ".docx";
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(docBytes);
                zip.closeEntry();
            }

            zip.putNextEntry(new ZipEntry("Javoblar_kaliti.xlsx"));
            zip.write(buildAnswerKeyExcel(answerKeys));
            zip.closeEntry();
        } catch (IOException e) {
            log.error("Variantlarni yaratishda xatolik", e);
            throw new RuntimeException("❌ Variantlarni yaratishda xatolik", e);
        }

        return zipBytes.toByteArray();
    }

    // Har bir MAVZUGA nechta savol berilishini hisoblaydi — savollar soniga
    // QARAB EMAS, mavzular soniga qarab TENG (masalan 3 ta mavzu, 30 ta
    // savol kerak bo'lsa — har biriga 10 tadan). Agar biror mavzuda
    // yetarli savol bo'lmasa, o'sha mavzu o'zining bor sonida "to'lib"
    // qoladi, yetmagan qism qolgan (hali joyi bor) mavzularga yana TENG
    // bo'lib qayta taqsimlanadi — bu jarayon barcha mavzular to'lib
    // qolguncha yoki talab qondirilguncha davom etadi ("suv quyish"
    // algoritmi). Agar shu doiradagi JAMI savollar soni ham perVariant'dan
    // kam bo'lsa — aniq xato bilan to'xtaydi (o'zi tasodifiy kamaytirib
    // qo'ymaydi).
    private Map<Long, Integer> allocateEqually(List<Topic> topics, Map<Long, List<Question>> questionsByTopic, int perVariant, Random random) {
        List<Long> ids = topics.stream().map(Topic::getId).toList();
        Map<Long, Integer> capacity = new LinkedHashMap<>();
        for (Topic t : topics) {
            capacity.put(t.getId(), questionsByTopic.getOrDefault(t.getId(), List.of()).size());
        }
        // QuestionAllocationUtil IllegalArgumentException otadi (RuntimeException
        // avlodi) — bu yerdagi eski xabar matni ("...har bir variant uchun...")
        // saqlab qolinishi uchun shu yerda ATAYLAB alohida ushlanadi.
        try {
            return QuestionAllocationUtil.allocateEqually(ids, capacity, perVariant, random);
        } catch (IllegalArgumentException e) {
            int totalCapacity = capacity.values().stream().mapToInt(Integer::intValue).sum();
            throw new RuntimeException("❌ Yetarli savol yo'q: shu doirada jami " + totalCapacity +
                    " ta faol savol bor, lekin har bir variant uchun " + perVariant + " ta so'ralgan.", e);
        }
    }

    // allocation'ga (har mavzuga nechta savol) muvofiq, har mavzudan
    // TASODIFIY (pool shuffle qilingandan keyingi boshidan) savol tanlaydi.
    // sameQuestions=false rejimida har chaqiriqda BOSHQA natija beradi
    // (chunki pool har safar qayta shuffle qilinadi) — sameQuestions=true
    // rejimida esa faqat BIR MARTA chaqiriladi.
    private List<Question> pickFromTopics(List<Topic> topics, Map<Long, List<Question>> questionsByTopic,
                                           Map<Long, Integer> allocation, Random random) {
        List<Question> picked = new ArrayList<>();
        for (Topic t : topics) {
            List<Question> pool = new ArrayList<>(questionsByTopic.get(t.getId()));
            Collections.shuffle(pool, random);
            int take = allocation.getOrDefault(t.getId(), 0);
            picked.addAll(pool.subList(0, Math.min(take, pool.size())));
        }
        return picked;
    }

    private byte[] buildVariantDocument(String scopeTitle, int variantNumber, List<Question> questions,
                                         boolean shuffleAnswers, Random random, Map<Integer, String> variantKeyOut) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeVariantHeader(doc, scopeTitle, variantNumber);

            int number = 1;
            for (Question q : questions) {
                String correctLetter = writeVariantQuestion(doc, number, q, shuffleAnswers, random);
                variantKeyOut.put(number, correctLetter);
                number++;
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    // Sarlavha + imtihon varag'i uchun "F.I.Sh / Sana" qatori (o'qituvchi
    // bosib chiqarib, talabaga tarqatishga tayyor bo'lishi uchun).
    private void writeVariantHeader(XWPFDocument doc, String scopeTitle, int variantNumber) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(120);
        XWPFRun titleRun = title.createRun();
        titleRun.setText(scopeTitle + " — Variant " + variantNumber);
        titleRun.setBold(true);
        titleRun.setFontSize(16);

        XWPFParagraph info = doc.createParagraph();
        info.setSpacingAfter(300);
        XWPFRun infoRun = info.createRun();
        infoRun.setText("F.I.Sh: ______________________________        Sana: ______________");
        infoRun.setFontSize(12);
    }

    // Bitta savol bloki — WordService#writeQuestion bilan bir xil
    // ko'rinish, lekin qo'shimcha: shuffleAnswers=true bo'lsa javoblar
    // tartibi shu variantda ARALASHTIRILADI (ko'chirib yozishni
    // qiyinlashtirish uchun), va to'g'ri javob harfi (aralashtirilgandan
    // KEYINGI holati) qaytariladi — javoblar kaliti uchun.
    private String writeVariantQuestion(XWPFDocument doc, int number, Question q, boolean shuffleAnswers, Random random) {
        XWPFParagraph questionPara = doc.createParagraph();
        questionPara.setSpacingBefore(240);
        questionPara.setSpacingAfter(80);

        XWPFRun questionRun = questionPara.createRun();
        questionRun.setText(number + ". " + q.getQuestionText());
        questionRun.setBold(true);
        questionRun.setFontSize(12);

        DocxImageUtil.insertImageIfPresent(doc, uploadDir, q.getImageUrl());

        List<Answer> answers = q.getAnswers() == null
                ? new ArrayList<>()
                : new ArrayList<>(q.getAnswers().stream()
                        .sorted(Comparator.comparing(Answer::getId))
                        .limit(5)
                        .toList());

        if (shuffleAnswers) {
            Collections.shuffle(answers, random);
        }

        // Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi, 2026-09-06)
        // — BARCHA to'g'ri harflar to'planadi ("B,D"), faqat OXIRGISI EMAS.
        // Avval bitta "String correctLetter" har safar ustidan yozilardi —
        // ExcelService#writeAnswerColumns'da (2-bosqich) allaqachon topilib
        // tuzatilgan xuddi shu bug shu yerga ATAYLAB tegilmagan edi.
        List<String> correctLetters = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            Answer a = answers.get(i);

            XWPFParagraph answerPara = doc.createParagraph();
            answerPara.setIndentationLeft(400);
            answerPara.setSpacingAfter(40);

            XWPFRun answerRun = answerPara.createRun();
            answerRun.setText(ANSWER_LETTERS[i] + ") " + a.getAnswerText());
            answerRun.setFontSize(11);

            DocxImageUtil.insertImageIfPresent(doc, uploadDir, a.getImageUrl());

            if (Boolean.TRUE.equals(a.getIsTrue())) {
                correctLetters.add(ANSWER_LETTERS[i]);
            }
        }

        return String.join(",", correctLetters);
    }

    // O'qituvchi uchun javoblar kaliti — har bir qator bitta variant,
    // ustunlar esa savol tartib raqamlari (1,2,3...), katakda o'sha
    // savolning to'g'ri javob harfi.
    private byte[] buildAnswerKeyExcel(List<Map<Integer, String>> answerKeys) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Javoblar kaliti");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int maxQuestions = answerKeys.stream().mapToInt(Map::size).max().orElse(0);

            Row header = sheet.createRow(0);
            Cell firstHeader = header.createCell(0);
            firstHeader.setCellValue("Variant");
            firstHeader.setCellStyle(headerStyle);
            for (int q = 1; q <= maxQuestions; q++) {
                Cell cell = header.createCell(q);
                cell.setCellValue(q);
                cell.setCellStyle(headerStyle);
            }

            sheet.setColumnWidth(0, 12 * 256);
            for (int q = 1; q <= maxQuestions; q++) {
                sheet.setColumnWidth(q, 6 * 256);
            }

            for (int v = 0; v < answerKeys.size(); v++) {
                Row row = sheet.createRow(v + 1);
                row.createCell(0).setCellValue("Variant " + (v + 1));

                Map<Integer, String> key = answerKeys.get(v);
                for (int q = 1; q <= maxQuestions; q++) {
                    row.createCell(q).setCellValue(key.getOrDefault(q, ""));
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }
}
