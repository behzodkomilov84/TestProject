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
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

// "📝 Word'ga eksport" — Excel eksportga o'xshab, lekin chop etishga
// (bosib chiqarishga) qulay .docx format uchun: tepada mavzu/bo'lim/fan
// nomi sarlavha sifatida, pastda savollar tartib raqami bilan, har biri
// javoblari A/B/C/D/E sifatida. Izoh va to'g'ri javob KIRITILMAYDI —
// ExcelService'dan farqli, bu eksport hisobot emas, aynan shu ko'rinishda
// bosib chiqarish/tarqatish uchun mo'ljallangan.
@Slf4j
@Service
@RequiredArgsConstructor
public class WordService {

    private static final String[] ANSWER_LETTERS = {"A", "B", "C", "D", "E"};

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final TopicSectionRepository topicSectionRepository;
    private final ScienceRepository scienceRepository;

    // Savol/javobga biriktirilgan rasmni diskdan o'qish uchun (DocxImageUtil) —
    // FileStorageService bilan bir xil manba (application.yaml: app.upload.dir).
    @Value("${app.upload.dir}")
    private String uploadDir;

    // Mavzu miqyosida — bitta mavzudagi barcha faol savollar (question.js).
    @Transactional(readOnly = true)
    public ExportedFileDto exportQuestionsToWord(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Mavzu topilmadi: " + topicId));
        List<Question> questions = questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(topicId);

        byte[] data = buildDocument("Mavzu: " + topic.getName(), List.of(topic), List.of(questions));
        return new ExportedFileDto(data, "savollar_" + ExportFilenameUtil.sanitize(topic.getName()));
    }

    // Bo'lim miqyosida — shu Bo'limdagi BARCHA mavzularning savollari
    // BITTA faylga yig'iladi, har bir mavzu O'Z sahifasidan boshlanadi
    // (topicSection.js).
    @Transactional(readOnly = true)
    public ExportedFileDto exportQuestionsForSection(Long sectionId) {
        TopicSection section = topicSectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Bo'lim topilmadi: " + sectionId));
        List<Topic> topics = topicRepository.findBySection_IdAndDeletedAtIsNullOrderByOrderIndexAsc(sectionId);

        byte[] data = buildDocument("Bo'lim: " + section.getName(), topics, questionsPerTopic(topics));
        return new ExportedFileDto(data, "savollar_bolim_" + ExportFilenameUtil.sanitize(section.getName()));
    }

    // Fan miqyosida — shu Fandagi BARCHA mavzularning savollari BITTA
    // faylga yig'iladi, har bir mavzu O'Z sahifasidan boshlanadi
    // (science.js).
    @Transactional(readOnly = true)
    public ExportedFileDto exportQuestionsForScience(Long scienceId) {
        Science science = scienceRepository.findById(scienceId)
                .orElseThrow(() -> new RuntimeException("Fan topilmadi: " + scienceId));
        List<Topic> topics = topicRepository.findByScience_IdAndDeletedAtIsNullOrderByOrderIndexAsc(scienceId);

        byte[] data = buildDocument("Fan: " + science.getName(), topics, questionsPerTopic(topics));
        return new ExportedFileDto(data, "savollar_fan_" + ExportFilenameUtil.sanitize(science.getName()));
    }

    private List<List<Question>> questionsPerTopic(List<Topic> topics) {
        return topics.stream()
                .map(t -> questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(t.getId()))
                .toList();
    }

    // Umumiy quruvchi — bitta mavzulik eksportda "topics"/"questionsPerTopic"
    // bittadan iborat bo'ladi (mavzu sarlavhasi qo'shilmaydi, faqat ustki
    // sarlavha), bir nechta mavzu bo'lsa — har biri O'Z sarlavhasi va O'Z
    // sahifasida (savollar bir-biriga aralashib ko'rinmasligi uchun).
    private byte[] buildDocument(String docTitle, List<Topic> topics, List<List<Question>> questionsPerTopic) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeTitle(doc, docTitle);

            boolean multiTopic = topics.size() > 1;
            for (int t = 0; t < topics.size(); t++) {
                if (multiTopic) {
                    writeTopicHeading(doc, topics.get(t).getName(), t > 0);
                }

                int number = 1;
                for (Question q : questionsPerTopic.get(t)) {
                    writeQuestion(doc, number++, q);
                }
            }

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Wordga eksport qilishda xatolik", e);
            throw new RuntimeException("❌ Wordga eksport qilishda xatolik", e);
        }
    }

    // Ustki sarlavha — "Mavzu:/Bo'lim:/Fan: <nomi>", markazlashtirilgan,
    // qalin va yirik shriftda, savollar ro'yxatidan pastroqqa bo'sh joy
    // bilan ajratilgan (savollar bilan "yopishib" qolmasin deb).
    private void writeTitle(XWPFDocument doc, String text) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(300);

        XWPFRun run = title.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(16);
    }

    // Bo'lim/Fan miqyosidagi eksportda — har bir MAVZU o'z sarlavhasi
    // bilan ajratiladi. Birinchisidan boshqasi YANGI SAHIFADAN boshlanadi
    // (page break) — bir mavzuning savollari boshqasiniki bilan
    // "aralashib" ko'rinmasin deb.
    private void writeTopicHeading(XWPFDocument doc, String topicName, boolean pageBreakBefore) {
        XWPFParagraph heading = doc.createParagraph();
        heading.setSpacingBefore(pageBreakBefore ? 0 : 200);
        heading.setSpacingAfter(200);

        XWPFRun run = heading.createRun();
        if (pageBreakBefore) {
            run.addBreak(BreakType.PAGE);
        }
        run.setText("Mavzu: " + topicName);
        run.setBold(true);
        run.setFontSize(14);
        run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
    }

    // Bitta savol bloki — "N. savol matni" (qalin) va ostida chekinib
    // turgan "A) ...", "B) ..." javoblar. Har bir savoldan keyin yetarli
    // bo'shliq qoldiriladi (spacingBefore) — savollar bir-biriga
    // "aralashib" ko'rinmasligi uchun.
    private void writeQuestion(XWPFDocument doc, int number, Question q) {
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

        for (int i = 0; i < answers.size(); i++) {
            Answer a = answers.get(i);

            XWPFParagraph answerPara = doc.createParagraph();
            answerPara.setIndentationLeft(400);
            answerPara.setSpacingAfter(40);

            XWPFRun answerRun = answerPara.createRun();
            answerRun.setText(ANSWER_LETTERS[i] + ") " + a.getAnswerText());
            answerRun.setFontSize(11);

            DocxImageUtil.insertImageIfPresent(doc, uploadDir, a.getImageUrl());
        }
    }
}
