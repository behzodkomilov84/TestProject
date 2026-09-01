package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dto.export.ExportedFileDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseChapter;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSectionType;
import behzoddev.testproject.entity.enums.VideoSourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// "📝 Kursni Word'ga eksport qilish" (courseDetail.js) — BUTUN kursni
// bitta .docx faylga yig'adi, 3 ta mustaqil qism (checkbox orqali
// yoqilib/o'chirilib tanlanadi), HAR DOIM shu tartibda:
//   1) KURS — har bir mavzuning o'zi (sarlavha + matn/video kontenti,
//      matn HtmlToDocxConverter orqali "imkon qadar maksimal" formatlash
//      bilan — qalin/kursiv, giperssilkalar, ro'yxatlar, jadvallar,
//      rasmlar, PPT slaydlar);
//   2) TESTLAR — shu mavzularga bog'langan test savollari (to'g'ri javob
//      BELGILANMAGAN holda — WordService bilan bir xil uslub);
//   3) JAVOBLAR — ENG OXIRIDA, ALOHIDA bo'lim: yuqoridagi testlarning
//      to'g'ri javoblari (foydalanuvchi ANIQ so'ragan tartib: kurs,
//      testlar, javoblar — testning o'zida to'g'ri javob ko'rinib
//      turmasin deb).
// Har ikkala 1) va 2) qism ICHIDA — Bo'lim (CourseChapter) bo'yicha
// guruhlangan, har bir Bo'lim yangi sahifadan boshlanadi (avvalgi
// eksportlar — WordService/ExamVariantService — bilan bir xil uslub).
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseWordExportService {

    private static final String[] ANSWER_LETTERS = {"A", "B", "C", "D", "E"};

    private final CourseService courseService;
    private final CourseSectionRepository courseSectionRepository;
    private final QuestionRepository questionRepository;

    // Savol/javob va kurs matni ichidagi (diskdagi) rasmlarni o'qish uchun
    // (DocxImageUtil) — FileStorageService bilan bir xil manba.
    @Value("${app.upload.dir}")
    private String uploadDir;

    // Video darslar uchun to'liq (bosiladigan) havola yasash uchun —
    // videoUrl UPLOAD manbada nisbiy ("/uploads/...") saqlanadi, hujjat
    // ilova/saytdan TASHQARIDA ochilgani uchun bu yerda to'liq domenga
    // muhtoj (TelegramAutoLoginService bilan bir xil manba).
    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    private record ChapterGroup(CourseChapter chapter, List<CourseSection> items) {
    }

    @Transactional(readOnly = true)
    public ExportedFileDto exportCourse(Long courseId, boolean includeContent, boolean includeTests,
                                         boolean includeAnswers, User currentUser) {
        Course course = courseService.requireManageableCourse(courseId, currentUser);
        List<CourseSection> sections = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId);
        List<ChapterGroup> groups = groupByChapter(sections);

        byte[] data = buildDocument(course.getTitle(), groups, includeContent, includeTests, includeAnswers);
        return new ExportedFileDto(data, ExportFilenameUtil.sanitize(course.getTitle()));
    }

    private List<ChapterGroup> groupByChapter(List<CourseSection> sections) {
        Map<Long, ChapterGroup> byChapterId = new LinkedHashMap<>();
        List<CourseSection> unlinked = new ArrayList<>();

        for (CourseSection s : sections) {
            if (s.getChapter() != null) {
                byChapterId.computeIfAbsent(s.getChapter().getId(), id -> new ChapterGroup(s.getChapter(), new ArrayList<>()))
                        .items().add(s);
            } else {
                unlinked.add(s);
            }
        }

        List<ChapterGroup> result = new ArrayList<>(byChapterId.values());
        result.sort(Comparator.comparingInt(g -> g.chapter().getOrderIndex()));
        if (!unlinked.isEmpty()) {
            result.add(new ChapterGroup(null, unlinked));
        }
        return result;
    }

    private byte[] buildDocument(String courseTitle, List<ChapterGroup> groups, boolean includeContent,
                                  boolean includeTests, boolean includeAnswers) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDocTitle(doc, courseTitle);

            if (includeContent) {
                writeContentSection(doc, groups);
            }

            Map<Long, List<String>> answersBySectionId = new LinkedHashMap<>();
            if (includeTests) {
                writeTestsSection(doc, groups, answersBySectionId);
            }

            if (includeAnswers && !answersBySectionId.isEmpty()) {
                writeAnswersSection(doc, groups, answersBySectionId);
            }

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Kursni Word'ga eksport qilishda xatolik", e);
            throw new RuntimeException("❌ Kursni Word'ga eksport qilishda xatolik", e);
        }
    }

    private void writeDocTitle(XWPFDocument doc, String courseTitle) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(300);
        XWPFRun run = title.createRun();
        run.setText(courseTitle);
        run.setBold(true);
        run.setFontSize(20);
    }

    // "TESTLAR"/"JAVOBLAR" kabi hujjatning YIRIK, mustaqil qismlari —
    // har doim YANGI sahifadan boshlanadi.
    private void writeSectionTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(300);
        XWPFRun run = p.createRun();
        run.addBreak(BreakType.PAGE);
        run.setText(text);
        run.setBold(true);
        run.setFontSize(18);
    }

    private String chapterLabel(ChapterGroup group) {
        return group.chapter() != null ? "Bo'lim: " + group.chapter().getName() : "Bo'limsiz mavzular";
    }

    private void writeChapterHeading(XWPFDocument doc, String text, boolean pageBreakBefore) {
        XWPFParagraph heading = doc.createParagraph();
        heading.setSpacingBefore(pageBreakBefore ? 0 : 200);
        heading.setSpacingAfter(200);
        XWPFRun run = heading.createRun();
        if (pageBreakBefore) {
            run.addBreak(BreakType.PAGE);
        }
        run.setText(text);
        run.setBold(true);
        run.setFontSize(15);
        run.setUnderline(UnderlinePatterns.SINGLE);
    }

    private void writeTopicSubheading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(160);
        p.setSpacingAfter(80);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(13);
    }

    // ===================== 1) KURS =====================

    private void writeContentSection(XWPFDocument doc, List<ChapterGroup> groups) {
        boolean first = true;
        for (ChapterGroup group : groups) {
            if (group.items().isEmpty()) {
                continue;
            }
            writeChapterHeading(doc, chapterLabel(group), !first);
            first = false;

            int number = 1;
            for (CourseSection s : group.items()) {
                writeTopicSubheading(doc, number++ + ". " + s.getTitle());

                if (s.getType() == CourseSectionType.VIDEO || s.getType() == CourseSectionType.MIXED) {
                    writeVideoNote(doc, s);
                }
                if (s.getType() == CourseSectionType.TEXT || s.getType() == CourseSectionType.MIXED) {
                    HtmlToDocxConverter.convert(doc, s.getTextContent(), s.getTextContentFormat(), uploadDir);
                }
            }
        }
    }

    private void writeVideoNote(XWPFDocument doc, CourseSection s) {
        VideoSourceType sourceType = s.getVideoSourceType();
        String videoUrl = s.getVideoUrl();
        if (sourceType == null || videoUrl == null || videoUrl.isBlank()) {
            return;
        }

        String url = switch (sourceType) {
            case UPLOAD -> publicBaseUrl + videoUrl;
            case YOUTUBE -> "https://youtu.be/" + videoUrl;
            case EXTERNAL -> videoUrl;
        };

        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(100);
        XWPFRun label = p.createRun();
        label.setBold(true);
        label.setFontSize(11);
        label.setText("🎬 Video: ");

        XWPFRun link = p.createHyperlinkRun(url);
        link.setText(url);
        link.setColor("0563C1");
        link.setUnderline(UnderlinePatterns.SINGLE);
        link.setFontSize(11);
    }

    // ===================== 2) TESTLAR =====================

    private void writeTestsSection(XWPFDocument doc, List<ChapterGroup> groups, Map<Long, List<String>> answersBySectionId) {
        writeSectionTitle(doc, "TESTLAR");

        boolean first = true;
        for (ChapterGroup group : groups) {
            List<CourseSection> withTests = group.items().stream()
                    .filter(s -> s.getLinkedTopic() != null)
                    .toList();
            if (withTests.isEmpty()) {
                continue;
            }

            writeChapterHeading(doc, chapterLabel(group), !first);
            first = false;

            for (CourseSection s : withTests) {
                List<Question> questions = questionRepository
                        .findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(s.getLinkedTopic().getId());
                if (questions.isEmpty()) {
                    continue;
                }

                writeTopicSubheading(doc, s.getTitle());

                List<String> letters = new ArrayList<>();
                int number = 1;
                for (Question q : questions) {
                    letters.add(writeQuestion(doc, number++, q));
                }
                answersBySectionId.put(s.getId(), letters);
            }
        }
    }

    // WordService#writeQuestion bilan bir xil ko'rinish (to'g'ri javob
    // BELGILANMAYDI) — farqi: to'g'ri javob harfini qaytaradi (javoblar
    // kaliti uchun, matnda emas).
    private String writeQuestion(XWPFDocument doc, int number, Question q) {
        XWPFParagraph questionPara = doc.createParagraph();
        questionPara.setSpacingBefore(240);
        questionPara.setSpacingAfter(80);

        XWPFRun questionRun = questionPara.createRun();
        questionRun.setText(number + ". " + q.getQuestionText());
        questionRun.setBold(true);
        questionRun.setFontSize(12);

        DocxImageUtil.insertImageIfPresent(doc, uploadDir, q.getImageUrl());

        List<Answer> answers = q.getAnswers() == null
                ? List.of()
                : q.getAnswers().stream()
                        .sorted(Comparator.comparing(Answer::getId))
                        .limit(5)
                        .toList();

        String correctLetter = "";
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
                correctLetter = ANSWER_LETTERS[i];
            }
        }

        return correctLetter;
    }

    // ===================== 3) JAVOBLAR =====================

    private void writeAnswersSection(XWPFDocument doc, List<ChapterGroup> groups, Map<Long, List<String>> answersBySectionId) {
        writeSectionTitle(doc, "JAVOBLAR");

        boolean first = true;
        for (ChapterGroup group : groups) {
            List<CourseSection> withAnswers = group.items().stream()
                    .filter(s -> answersBySectionId.containsKey(s.getId()))
                    .toList();
            if (withAnswers.isEmpty()) {
                continue;
            }

            writeChapterHeading(doc, chapterLabel(group), !first);
            first = false;

            for (CourseSection s : withAnswers) {
                writeTopicSubheading(doc, s.getTitle());

                List<String> letters = answersBySectionId.get(s.getId());
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(80);
                XWPFRun run = p.createRun();
                run.setFontSize(11);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < letters.size(); i++) {
                    if (i > 0) {
                        sb.append("    ");
                    }
                    sb.append(i + 1).append(". ").append(letters.get(i));
                }
                run.setText(sb.toString());
            }
        }
    }
}
