package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.answer.AnswerShortDto;
import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.dto.question.QuestionShortDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.AnswerMapper;
import behzoddev.testproject.mapper.QuestionMapper;
import behzoddev.testproject.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuestionMapper/AnswerMapper — MapStruct tomonidan generatsiya qilinadigan
 * oddiy o'giruvchilar, shuning uchun mock qilinadi (o'zlarining mantig'ini
 * emas, QuestionService'ning ular bilan qanday ishlashini tekshiramiz).
 * Validation — haqiqiy klass (faqat uning AnswerService bog'liqligi mock
 * qilinadi), shunda dublikat/bo'sh-maydon tekshiruvlari qayta yozilmaydi.
 */
@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private AnswerMapper answerMapper;
    @Mock
    private AnswerService answerService;

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(answerService);
        questionService = new QuestionService(answerRepository, questionRepository, topicRepository,
                questionMapper, answerMapper, validation);
        lenientIsUniqueTrueByDefault();
    }

    private void lenientIsUniqueTrueByDefault() {
        org.mockito.Mockito.lenient().when(answerService.isUnique(any())).thenReturn(true);
    }

    private AnswerShortDto answer(String text, boolean isTrue) {
        return AnswerShortDto.builder().answerText(text).isTrue(isTrue).commentary("izoh").build();
    }

    // ===== isQuestionWithAnswersExists(QuestionShortDto) =====

    @Test
    void isQuestionWithAnswersExists_short_sameTextAndAnswersDifferentCaseAndOrder_isDuplicate() {
        QuestionShortDto existing = new QuestionShortDto("Savol1", null,
                List.of(answer("A", true), answer("B", false)));
        QuestionShortDto candidate = new QuestionShortDto("savol1", null,
                List.of(answer("b", false), answer("a", true)));

        boolean result = questionService.isQuestionWithAnswersExists(List.of(existing), candidate);

        assertThat(result).isTrue();
    }

    @Test
    void isQuestionWithAnswersExists_short_differentAnswerCount_notDuplicate() {
        QuestionShortDto existing = new QuestionShortDto("Savol1", null,
                List.of(answer("A", true), answer("B", false)));
        QuestionShortDto candidate = new QuestionShortDto("Savol1", null,
                List.of(answer("A", true)));

        boolean result = questionService.isQuestionWithAnswersExists(List.of(existing), candidate);

        assertThat(result).isFalse();
    }

    @Test
    void isQuestionWithAnswersExists_short_differentQuestionText_notDuplicate() {
        QuestionShortDto existing = new QuestionShortDto("Savol1", null,
                List.of(answer("A", true), answer("B", false)));
        QuestionShortDto candidate = new QuestionShortDto("Boshqa savol", null,
                List.of(answer("A", true), answer("B", false)));

        boolean result = questionService.isQuestionWithAnswersExists(List.of(existing), candidate);

        assertThat(result).isFalse();
    }

    // ===== isQuestionWithAnswersExists(QuestionSaveDto) — matn to'plami bo'yicha =====

    @Test
    void isQuestionWithAnswersExists_save_sameAnswerTextSet_isDuplicate() {
        QuestionSaveDto existing = QuestionSaveDto.builder().topicId(1L).questionText("Savol1")
                .answers(List.of(answer("A", true), answer("B", false))).build();
        QuestionSaveDto candidate = QuestionSaveDto.builder().topicId(1L).questionText("savol1")
                .answers(List.of(answer(" b ", false), answer("a", true))).build();

        boolean result = questionService.isQuestionWithAnswersExists(List.of(existing), candidate);

        assertThat(result).isTrue();
    }

    @Test
    void isQuestionWithAnswersExists_save_comparesOnlyAnswerTextNotIsTrueFlag() {
        // Diqqat: normalizeAnswer() faqat answerText'ni solishtiradi, isTrue'ni
        // emas — shuning uchun matn bir xil bo'lsa, to'g'ri/noto'g'ri belgisi
        // farq qilsa ham hozirgi kodda baribir dublikat deb topiladi. Bu test
        // shu haqiqiy xatti-harakatni qayd etib qo'yadi (regressiyani ushlaydi).
        QuestionSaveDto existing = QuestionSaveDto.builder().topicId(1L).questionText("Savol1")
                .answers(List.of(answer("A", true), answer("B", false))).build();
        QuestionSaveDto candidate = QuestionSaveDto.builder().topicId(1L).questionText("Savol1")
                .answers(List.of(answer("A", false), answer("B", true))).build();

        boolean result = questionService.isQuestionWithAnswersExists(List.of(existing), candidate);

        assertThat(result).isTrue();
    }

    @Test
    void isQuestionWithAnswersExists_save_differentAnswerSet_notDuplicate() {
        QuestionSaveDto existing = QuestionSaveDto.builder().topicId(1L).questionText("Savol1")
                .answers(List.of(answer("A", true), answer("B", false))).build();
        QuestionSaveDto candidate = QuestionSaveDto.builder().topicId(1L).questionText("Savol1")
                .answers(List.of(answer("C", true), answer("D", false))).build();

        boolean result = questionService.isQuestionWithAnswersExists(List.of(existing), candidate);

        assertThat(result).isFalse();
    }

    // ===== saveQuestion =====

    @Test
    void saveQuestion_success_setsTopicAndLinksAnswers() {
        Topic topic = Topic.builder().id(1L).name("Mavzu").build();
        QuestionShortDto dto = new QuestionShortDto("Yangi savol", null,
                List.of(answer("A", true), answer("B", false)));

        Answer a1 = Answer.builder().answerText("A").isTrue(true).commentary("izoh").build();
        Answer a2 = Answer.builder().answerText("B").isTrue(false).commentary("izoh").build();
        Question mapped = Question.builder().questionText("Yangi savol").answers(List.of(a1, a2)).build();

        when(questionMapper.mapQuestionShortDtoToQuestion(dto)).thenReturn(mapped);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(questionRepository.save(mapped)).thenReturn(mapped);

        Question result = questionService.saveQuestion(1L, dto);

        assertThat(result.getTopic()).isEqualTo(topic);
        assertThat(a1.getQuestion()).isEqualTo(mapped);
        assertThat(a2.getQuestion()).isEqualTo(mapped);
    }

    @Test
    void saveQuestion_blankQuestionText_throwsBeforeMapping() {
        QuestionShortDto dto = new QuestionShortDto("   ", null, List.of());

        assertThatThrownBy(() -> questionService.saveQuestion(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bo'sh bo'lishi mumkin emas");
    }

    @Test
    void saveQuestion_blankAnswerText_throws() {
        Topic topic = Topic.builder().id(1L).name("Mavzu").build();
        QuestionShortDto dto = new QuestionShortDto("Savol", null, List.of(answer("", true)));

        Answer blankAnswer = Answer.builder().answerText("").isTrue(true).commentary("izoh").build();
        Question mapped = Question.builder().questionText("Savol").answers(List.of(blankAnswer)).build();
        when(questionMapper.mapQuestionShortDtoToQuestion(dto)).thenReturn(mapped);

        assertThatThrownBy(() -> questionService.saveQuestion(1L, dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(questionRepository, org.mockito.Mockito.never()).save(any());
    }

    // ===== deleteQuestion =====

    @Test
    void deleteQuestion_success() {
        Question question = Question.builder().id(5L).questionText("Savol").build();
        when(questionRepository.findById(5L)).thenReturn(Optional.of(question));

        questionService.deleteQuestion(5L);

        verify(questionRepository).delete(question);
    }

    @Test
    void deleteQuestion_notFound_throws() {
        when(questionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.deleteQuestion(5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Question not found");
    }

    // ===== getQuestionById =====

    @Test
    void getQuestionById_success_delegatesToMapper() {
        Question question = Question.builder().id(5L).questionText("Savol").build();
        QuestionDto dto = QuestionDto.builder().id(5L).questionText("Savol").answers(List.of()).build();

        when(questionRepository.getQuestionById(5L)).thenReturn(question);
        when(questionMapper.mapQuestiontoQuestionDto(question)).thenReturn(dto);

        QuestionDto result = questionService.getQuestionById(5L);

        assertThat(result).isEqualTo(dto);
    }
}
