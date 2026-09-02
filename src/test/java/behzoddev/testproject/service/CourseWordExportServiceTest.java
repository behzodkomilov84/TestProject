package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseChapterRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dto.export.ExportedFileDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseChapter;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSectionContentFormat;
import behzoddev.testproject.entity.enums.CourseSectionType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * "📝 Kursni Word'ga eksport qilish" — 3 mustaqil qism (kurs matni/
 * testlar/javoblar), HAR DOIM shu tartibda, "Javoblar" ENG OXIRIDA
 * alohida bo'lim sifatida (testning o'zida to'g'ri javob belgilanmaydi —
 * foydalanuvchi ANIQ shu tartibni so'ragan).
 */
@ExtendWith(MockitoExtension.class)
class CourseWordExportServiceTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private CourseChapterRepository courseChapterRepository;
    @Mock
    private QuestionRepository questionRepository;

    private CourseWordExportService service() {
        CourseWordExportService s = new CourseWordExportService(courseService, courseSectionRepository, courseChapterRepository, questionRepository);
        ReflectionTestUtils.setField(s, "uploadDir", "uploads");
        ReflectionTestUtils.setField(s, "publicBaseUrl", "https://study-grow.uz");
        return s;
    }

    // CourseService (canManageCourse'ning haqiqiy tekshiruvi) shu testda
    // TO'LIQ mocklangan (requireManageableCourse), shu sabab haqiqiy rol
    // shart emas — faqat when()/chaqiruv orasida BIR XIL obyekt kifoya.
    private User owner() {
        return User.builder().id(1L).username("owner").build();
    }

    // XWPFParagraph#getText() sarlavhalardagi sahifa o'tkazish belgisini
    // (BreakType.PAGE, matn bilan BIR XIL run'da) boshidagi "\n" sifatida
    // qaytaradi — bu haqiqiy hujjatda ko'rinmaydi, faqat matn ajratib
    // olishdagi POI xususiyati, shu sabab tekshiruvda trim() bilan
    // yumshatiladi.
    private static List<String> paragraphTexts(byte[] docx) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            return doc.getParagraphs().stream().map(p -> p.getText().trim()).toList();
        }
    }

    @Test
    void exportCourse_notManageable_propagatesAccessDenied() {
        User owner = owner();
        when(courseService.requireManageableCourse(1L, owner))
                .thenThrow(new AccessDeniedException("⛔ ruxsat yo'q"));

        CourseWordExportService service = service();
        assertThatThrownBy(() -> service.exportCourse(1L, true, true, true, owner))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void exportCourse_contentOnly_writesChapterAndTopicWithTextButNoTestsOrAnswers() throws IOException {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Bakteriologiya kursi").createdBy(owner).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseSection section = CourseSection.builder().id(100L).course(course).chapter(chapter)
                .title("Kirish darsi").orderIndex(1).type(CourseSectionType.TEXT)
                .textContent("Oddiy matn kontenti").textContentFormat(CourseSectionContentFormat.PLAIN)
                .build();

        when(courseService.requireManageableCourse(1L, owner)).thenReturn(course);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section));

        CourseWordExportService service = service();
        ExportedFileDto result = service.exportCourse(1L, true, false, false, owner);

        assertThat(result.filenameBase()).isEqualTo("Bakteriologiya_kursi");

        List<String> texts = paragraphTexts(result.data());
        assertThat(texts).contains("Bakteriologiya kursi", "Bo'lim: 1-bob", "1. Kirish darsi", "Oddiy matn kontenti");
        assertThat(texts).noneMatch(t -> t.equals("TESTLAR") || t.equals("JAVOBLAR"));
        org.mockito.Mockito.verifyNoInteractions(questionRepository);
    }

    @Test
    void exportCourse_testsWithoutAnswers_questionsPresentButNoAnswersSection() throws IOException {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();
        Topic topic = Topic.builder().id(50L).name("Mavzu").build();
        CourseSection section = CourseSection.builder().id(100L).course(course).chapter(null)
                .title("Dars").orderIndex(1).type(CourseSectionType.TEXT).linkedTopic(topic)
                .textContent("matn").textContentFormat(CourseSectionContentFormat.PLAIN)
                .build();

        Answer correct = Answer.builder().id(1L).answerText("To'g'ri javob").isTrue(true).build();
        Answer wrong = Answer.builder().id(2L).answerText("Noto'g'ri javob").isTrue(false).build();
        Question question = Question.builder().id(200L).questionText("Savol matni?")
                .answers(List.of(correct, wrong)).build();

        when(courseService.requireManageableCourse(1L, owner)).thenReturn(course);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(50L)).thenReturn(List.of(question));

        CourseWordExportService service = service();
        ExportedFileDto result = service.exportCourse(1L, false, true, false, owner);

        List<String> texts = paragraphTexts(result.data());
        assertThat(texts).contains("TESTLAR", "Bo'limsiz mavzular", "Dars", "1. Savol matni?", "A) To'g'ri javob", "B) Noto'g'ri javob");
        assertThat(texts).noneMatch(t -> t.equals("JAVOBLAR"));
        // Savol matnining o'zida to'g'ri javob HECH QANDAY tarzda (✅, qalin
        // belgi va h.k.) ko'rsatilmasligi kerak — foydalanuvchi ANIQ shuni so'ragan.
        assertThat(texts).noneMatch(t -> t.contains("✅") || t.contains("To'g'ri javob:"));
    }

    @Test
    void exportCourse_testsAndAnswers_answersSectionComesLastWithCorrectLetters() throws IOException {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();
        Topic topic = Topic.builder().id(50L).name("Mavzu").build();
        CourseSection section = CourseSection.builder().id(100L).course(course).chapter(null)
                .title("Dars").orderIndex(1).type(CourseSectionType.TEXT).linkedTopic(topic)
                .textContent("matn").textContentFormat(CourseSectionContentFormat.PLAIN)
                .build();

        // 2-javob (indeks 1, "B") to'g'ri.
        Answer a = Answer.builder().id(1L).answerText("Noto'g'ri").isTrue(false).build();
        Answer b = Answer.builder().id(2L).answerText("To'g'ri").isTrue(true).build();
        Question question = Question.builder().id(200L).questionText("Savol?").answers(List.of(a, b)).build();

        when(courseService.requireManageableCourse(1L, owner)).thenReturn(course);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section));
        when(questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(50L)).thenReturn(List.of(question));

        CourseWordExportService service = service();
        ExportedFileDto result = service.exportCourse(1L, true, true, true, owner);

        List<String> texts = paragraphTexts(result.data());

        int testsIdx = texts.indexOf("TESTLAR");
        int answersIdx = texts.indexOf("JAVOBLAR");
        int contentTitleIdx = texts.indexOf("1. Dars");

        assertThat(contentTitleIdx).isGreaterThanOrEqualTo(0);
        assertThat(testsIdx).isGreaterThan(contentTitleIdx);
        assertThat(answersIdx).isGreaterThan(testsIdx);

        // Javoblar bo'limida — "1. B" (savol raqami + to'g'ri harf), lekin
        // TESTLAR bo'limidagi savol matnining o'zida hech qanday belgi yo'q.
        assertThat(texts).contains("1. B");
    }

    // "KURS" qismidagi mavzu sarlavhalariga ("N. Mavzu") HAQIQIY Word
    // "Heading1" uslubi qo'yilishi kerak (Navigatsiya panel/Mundarija) —
    // lekin Bo'lim sarlavhasiga ("Bo'lim: ...") EMAS, foydalanuvchi aniq
    // shu farqni so'ragan.
    @Test
    void exportCourse_topicHeadingUsesHeadingStyle_butChapterHeadingDoesNot() throws IOException {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseSection section = CourseSection.builder().id(100L).course(course).chapter(chapter)
                .title("Kirish darsi").orderIndex(1).type(CourseSectionType.TEXT)
                .textContent("matn").textContentFormat(CourseSectionContentFormat.PLAIN)
                .build();

        when(courseService.requireManageableCourse(1L, owner)).thenReturn(course);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section));

        CourseWordExportService service = service();
        ExportedFileDto result = service.exportCourse(1L, true, false, false, owner);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result.data()))) {
            XWPFParagraph topicHeading = doc.getParagraphs().stream()
                    .filter(p -> p.getText().trim().equals("1. Kirish darsi"))
                    .findFirst().orElseThrow();
            assertThat(topicHeading.getStyle()).isEqualTo("Heading1");

            XWPFParagraph chapterHeading = doc.getParagraphs().stream()
                    .filter(p -> p.getText().trim().equals("Bo'lim: 1-bob"))
                    .findFirst().orElseThrow();
            assertThat(chapterHeading.getStyle()).isNull();
        }
    }

    // Mavzu matni tugab, keyingi mavzu boshlanishidan oldin ko'zga
    // tashlanadigan bo'sh joy bo'lishi kerak (foydalanuvchi so'rovi).
    @Test
    void exportCourse_betweenTwoTopics_hasBlankSpacerParagraphs() throws IOException {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseSection s1 = CourseSection.builder().id(100L).course(course).chapter(chapter)
                .title("Birinchi").orderIndex(1).type(CourseSectionType.TEXT)
                .textContent("matn1").textContentFormat(CourseSectionContentFormat.PLAIN).build();
        CourseSection s2 = CourseSection.builder().id(101L).course(course).chapter(chapter)
                .title("Ikkinchi").orderIndex(2).type(CourseSectionType.TEXT)
                .textContent("matn2").textContentFormat(CourseSectionContentFormat.PLAIN).build();

        when(courseService.requireManageableCourse(1L, owner)).thenReturn(course);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(s1, s2));

        CourseWordExportService service = service();
        ExportedFileDto result = service.exportCourse(1L, true, false, false, owner);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result.data()))) {
            List<String> texts = doc.getParagraphs().stream().map(p -> p.getText().trim()).toList();
            int firstTopicContentIdx = texts.indexOf("matn1");
            int secondTopicHeadingIdx = texts.indexOf("2. Ikkinchi");

            assertThat(firstTopicContentIdx).isGreaterThanOrEqualTo(0);
            assertThat(secondTopicHeadingIdx).isGreaterThan(firstTopicContentIdx);
            // Orada kamida 2 ta bo'sh paragraf bo'lishi kerak.
            List<String> between = texts.subList(firstTopicContentIdx + 1, secondTopicHeadingIdx);
            assertThat(between).filteredOn(String::isEmpty).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // "📝 Bo'limni Word'ga eksport qilish" — FAQAT shu bitta bo'limning
    // mavzulari yoziladi, boshqa bo'limlar (yoki bo'limsiz mavzular) YO'Q.
    @Test
    void exportChapter_writesOnlyThatChapterAndUsesChapterNameAsFilename() throws IOException {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(course).name("2-bob").orderIndex(2).build();
        CourseSection section = CourseSection.builder().id(100L).course(course).chapter(chapter)
                .title("Shu bo'lim darsi").orderIndex(1).type(CourseSectionType.TEXT)
                .textContent("shu bo'lim matni").textContentFormat(CourseSectionContentFormat.PLAIN)
                .build();

        when(courseService.requireManageableCourse(1L, owner)).thenReturn(course);
        when(courseChapterRepository.findById(10L)).thenReturn(java.util.Optional.of(chapter));
        when(courseSectionRepository.findByChapter_IdOrderByOrderIndexAsc(10L)).thenReturn(List.of(section));

        CourseWordExportService service = service();
        ExportedFileDto result = service.exportChapter(1L, 10L, true, false, false, owner);

        assertThat(result.filenameBase()).isEqualTo("2-bob");
        List<String> texts = paragraphTexts(result.data());
        assertThat(texts).contains("2-bob", "Bo'lim: 2-bob", "1. Shu bo'lim darsi", "shu bo'lim matni");
        org.mockito.Mockito.verifyNoInteractions(questionRepository);
    }

    @Test
    void exportChapter_chapterBelongsToDifferentCourse_throws() {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();
        Course otherCourse = Course.builder().id(2L).title("Boshqa kurs").createdBy(owner).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(otherCourse).name("Bob").orderIndex(1).build();

        when(courseService.requireManageableCourse(1L, owner)).thenReturn(course);
        when(courseChapterRepository.findById(10L)).thenReturn(java.util.Optional.of(chapter));

        CourseWordExportService service = service();
        assertThatThrownBy(() -> service.exportChapter(1L, 10L, true, true, true, owner))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
