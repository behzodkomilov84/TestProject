package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * "🎲 Variantlar yaratish" — mavzular bo'yicha TENG taqsimlash (savollar
 * soniga QARAB EMAS) va yetmagan qismni boshqa mavzularga qayta
 * taqsimlash ("suv quyish" algoritmi, ExamVariantService#allocateEqually)
 * to'g'ri ishlashini, zip'dagi har bir variant hujjatida savollarning
 * qaysi mavzudan kelganini (matn prefiksi orqali) tekshirib tasdiqlaydi.
 */
@ExtendWith(MockitoExtension.class)
class ExamVariantServiceTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicSectionRepository topicSectionRepository;
    @Mock
    private ScienceRepository scienceRepository;

    @TempDir
    Path tempDir;

    private ExamVariantService service;

    private ExamVariantService service() {
        return new ExamVariantService(questionRepository, topicRepository, topicSectionRepository, scienceRepository);
    }

    private Topic topic(long id, String name) {
        return Topic.builder().id(id).name(name).build();
    }

    private List<Question> questionsFor(String prefix, int count) {
        List<Question> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Question q = Question.builder().id((long) (prefix.hashCode() * 1000 + i)).questionText(prefix + "-Q" + i).build();
            List<Answer> answers = new ArrayList<>();
            for (int a = 0; a < 4; a++) {
                answers.add(Answer.builder().id((long) (i * 10 + a)).answerText(prefix + "-A" + a).isTrue(a == 0).question(q).build());
            }
            q.setAnswers(answers);
            list.add(q);
        }
        return list;
    }

    // Zip'dan har bir "Variant_*.docx" ichidagi savol matnlarini o'qib,
    // ularning prefiksi bo'yicha (T1/T2/T3) nechtadan kelganini sanaydi.
    private List<Map<String, Integer>> readVariantTopicCounts(byte[] zipBytes) throws IOException {
        List<Map<String, Integer>> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().startsWith("Variant_")) continue;

                byte[] docBytes = zip.readAllBytes();
                Map<String, Integer> counts = new HashMap<>();
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
                    for (XWPFParagraph p : doc.getParagraphs()) {
                        String text = p.getText();
                        // "N. T1-Q3" ko'rinishidagi savol qatorlari — prefiksni ajratib olamiz
                        if (text.matches("^\\d+\\.\\s+T\\d+-Q\\d+$")) {
                            String prefix = text.replaceAll("^\\d+\\.\\s+(T\\d+)-Q\\d+$", "$1");
                            counts.merge(prefix, 1, Integer::sum);
                        }
                    }
                }
                result.add(counts);
            }
        }
        return result;
    }

    private int countZipEntries(byte[] zipBytes, String prefix) throws IOException {
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().startsWith(prefix)) count++;
            }
        }
        return count;
    }

    @Test
    void generateVariantsForScience_equalSplitWithRedistribution_matchesWaterFilling() throws IOException {
        // T1: 2 ta savol, T2: 2 ta savol, T3: 10 ta savol — jami 14 ta,
        // har bir variantga 10 ta kerak. Teng taqsimlash: 10/3 ≈ 3 tadan,
        // lekin T1/T2'da atigi 2 tadan bor — ular 2 tada "to'lib" qoladi,
        // yetmagan 1+1=2 ta T3'ga qo'shilib, T3 dan 3+2=... suv quyish
        // natijasi: T1=2, T2=2, T3=6 (jami 10).
        Topic t1 = topic(1, "T1");
        Topic t2 = topic(2, "T2");
        Topic t3 = topic(3, "T3");
        Science science = Science.builder().id(9L).name("Fan").build();

        when(scienceRepository.findById(9L)).thenReturn(java.util.Optional.of(science));
        when(topicRepository.findByScience_IdAndDeletedAtIsNullOrderByOrderIndexAsc(9L)).thenReturn(List.of(t1, t2, t3));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(1L)).thenReturn(questionsFor("T1", 2));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(2L)).thenReturn(questionsFor("T2", 2));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(3L)).thenReturn(questionsFor("T3", 10));

        service = service();
        behzoddev.testproject.dto.export.ExportedFileDto result = service.generateVariantsForScience(9L, 5, 10, false, false);
        assertThat(result.filenameBase()).isEqualTo("variantlar_fan_Fan");
        byte[] zip = result.data();

        assertThat(countZipEntries(zip, "Variant_")).isEqualTo(5);
        assertThat(countZipEntries(zip, "Javoblar_kaliti.xlsx")).isEqualTo(1);

        List<Map<String, Integer>> perVariantCounts = readVariantTopicCounts(zip);
        assertThat(perVariantCounts).hasSize(5);
        for (Map<String, Integer> counts : perVariantCounts) {
            assertThat(counts.getOrDefault("T1", 0)).isEqualTo(2);
            assertThat(counts.getOrDefault("T2", 0)).isEqualTo(2);
            assertThat(counts.getOrDefault("T3", 0)).isEqualTo(6);
            assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
        }
    }

    // Zip'dan har bir "Variant_*.docx" ichidagi savol matnlarini
    // KETMA-KETLIGI bilan (ro'yxat sifatida) o'qiydi — sameQuestions=true
    // rejimida "bir xil savollar, boshqa tartib"ni tekshirish uchun.
    private List<List<String>> readVariantQuestionOrders(byte[] zipBytes) throws IOException {
        List<List<String>> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().startsWith("Variant_")) continue;

                byte[] docBytes = zip.readAllBytes();
                List<String> order = new ArrayList<>();
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
                    for (XWPFParagraph p : doc.getParagraphs()) {
                        String text = p.getText();
                        if (text.matches("^\\d+\\.\\s+T\\d+-Q\\d+$")) {
                            order.add(text.replaceAll("^\\d+\\.\\s+", ""));
                        }
                    }
                }
                result.add(order);
            }
        }
        return result;
    }

    @Test
    void generateVariantsForTopic_sameQuestionsTrue_sameContentDifferentOrderAcrossVariants() throws IOException {
        Topic t1 = topic(1, "T1");

        when(topicRepository.findById(1L)).thenReturn(java.util.Optional.of(t1));
        // Tartib aralashtirilishi ko'zga tashlanishi uchun yetarlicha katta pool
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(1L)).thenReturn(questionsFor("T1", 20));

        service = service();
        byte[] zip = service.generateVariantsForTopic(1L, 10, 20, false, true).data();

        List<List<String>> orders = readVariantQuestionOrders(zip);
        assertThat(orders).hasSize(10);

        // Tarkib (savollar TO'PLAMI) barcha nusxada BIR XIL bo'lishi kerak
        List<String> firstAsSet = orders.get(0).stream().sorted().toList();
        for (List<String> order : orders) {
            assertThat(order.stream().sorted().toList()).isEqualTo(firstAsSet);
        }

        // Lekin TARTIB kamida bitta juftlikda farq qilishi kerak
        // (20 ta savolning tasodifiy tartibi 10 marta bir xil chiqishi
        // amalda ehtimoldan yiroq — flaky bo'lmasligi uchun shunchaki
        // "kamida bitta farq bor"ligini tekshiramiz).
        boolean anyDifferentOrder = orders.stream().anyMatch(o -> !o.equals(orders.get(0)));
        assertThat(anyDifferentOrder).isTrue();
    }

    @Test
    void generateVariantsForScience_notEnoughQuestionsOverall_throwsClearError() {
        Topic t1 = topic(1, "T1");
        Topic t2 = topic(2, "T2");
        Science science = Science.builder().id(9L).name("Fan").build();

        when(scienceRepository.findById(9L)).thenReturn(java.util.Optional.of(science));
        when(topicRepository.findByScience_IdAndDeletedAtIsNullOrderByOrderIndexAsc(9L)).thenReturn(List.of(t1, t2));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(1L)).thenReturn(questionsFor("T1", 3));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(2L)).thenReturn(questionsFor("T2", 3));

        service = service();

        assertThatThrownBy(() -> service.generateVariantsForScience(9L, 3, 10, false, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Yetarli savol yo'q")
                .hasMessageContaining("jami 6");
    }

    @Test
    void generateVariantsForTopic_answerKeyExcel_hasOneRowPerVariantAndCorrectLetters() throws IOException {
        Topic t1 = topic(1, "T1");

        when(topicRepository.findById(1L)).thenReturn(java.util.Optional.of(t1));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(1L)).thenReturn(questionsFor("T1", 5));

        service = service();
        // shuffleAnswers=false -> to'g'ri javob har doim birinchi (A) bo'lib qoladi
        behzoddev.testproject.dto.export.ExportedFileDto result = service.generateVariantsForTopic(1L, 3, 5, false, false);
        assertThat(result.filenameBase()).isEqualTo("variantlar_T1");
        byte[] zip = result.data();

        Workbook wb = null;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals("Javoblar_kaliti.xlsx")) {
                    wb = WorkbookFactory.create(new ByteArrayInputStream(zin.readAllBytes()));
                }
            }
        }

        assertThat(wb).isNotNull();
        Sheet sheet = wb.getSheetAt(0);
        // 1 sarlavha qator + 3 variant qator
        assertThat(sheet.getLastRowNum()).isEqualTo(3);

        for (int r = 1; r <= 3; r++) {
            Row row = sheet.getRow(r);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Variant " + r);
            for (int c = 1; c <= 5; c++) {
                assertThat(row.getCell(c).getStringCellValue()).isEqualTo("A");
            }
        }
        wb.close();
    }

    // Haqiqiy topilgan bug (foydalanuvchi so'rovi, 2026-09-06) —
    // "correctLetter" bitta String edi, har bir to'g'ri javobda ustidan
    // yozilardi, natijada ko'p to'g'ri javobli savolda javoblar kalitida
    // faqat OXIRGI to'g'ri harf qolib, qolganlari yo'qolardi (xuddi
    // ExcelService#writeAnswerColumns'da 2-bosqichda topilib tuzatilgan
    // bug bilan bir xil, faqat shu yerga tegilmagan edi).
    @Test
    void generateVariantsForTopic_multiCorrectAnswerQuestion_answerKeyKeepsAllCorrectLetters() throws IOException {
        Topic t1 = topic(1, "T1");
        Question q = Question.builder().id(999L).questionText("T1-Q1").build();
        List<Answer> answers = new ArrayList<>();
        for (int a = 0; a < 4; a++) {
            // A (indeks 0) va C (indeks 2) — ikkalasi ham to'g'ri.
            boolean isTrue = (a == 0 || a == 2);
            answers.add(Answer.builder().id((long) (100 + a)).answerText("T1-A" + a).isTrue(isTrue).question(q).build());
        }
        q.setAnswers(answers);

        when(topicRepository.findById(1L)).thenReturn(java.util.Optional.of(t1));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(1L)).thenReturn(List.of(q));

        service = service();
        // shuffleAnswers=false -> javoblar tartibi o'zgarmaydi (A=indeks 0, C=indeks 2).
        behzoddev.testproject.dto.export.ExportedFileDto result = service.generateVariantsForTopic(1L, 1, 1, false, false);
        byte[] zip = result.data();

        Workbook wb = null;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals("Javoblar_kaliti.xlsx")) {
                    wb = WorkbookFactory.create(new ByteArrayInputStream(zin.readAllBytes()));
                }
            }
        }

        assertThat(wb).isNotNull();
        Sheet sheet = wb.getSheetAt(0);
        Row row = sheet.getRow(1);
        // Ikkala to'g'ri javob HAM saqlanishi kerak, faqat oxirgisi emas.
        assertThat(row.getCell(1).getStringCellValue()).isEqualTo("A,C");
        wb.close();
    }

    @Test
    void generateVariantsForTopic_questionWithImage_embedsPictureInDocx() throws IOException {
        Topic t1 = topic(1, "T1");

        Path questionsDir = Files.createDirectories(tempDir.resolve("questions"));
        Path imageFile = questionsDir.resolve("test.png");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", out);
            Files.write(imageFile, out.toByteArray());
        }

        Question q = Question.builder().id(1L).questionText("T1-Q1").imageUrl("/uploads/questions/test.png").build();
        Answer a = Answer.builder().id(1L).answerText("T1-A0").isTrue(true).question(q).build();
        q.setAnswers(List.of(a));

        when(topicRepository.findById(1L)).thenReturn(java.util.Optional.of(t1));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(1L)).thenReturn(List.of(q));

        service = service();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        byte[] zip = service.generateVariantsForTopic(1L, 1, 1, false, false).data();

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (!entry.getName().startsWith("Variant_")) continue;

                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(zin.readAllBytes()))) {
                    assertThat(doc.getAllPictures()).hasSize(1);
                }
            }
        }
    }
}
