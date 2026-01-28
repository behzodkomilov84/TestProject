package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.exception.ErrorResponse;
import behzoddev.testproject.mapper.AnswerMapper;
import behzoddev.testproject.mapper.QuestionMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final Validation validation;

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestionsByIds(Long scienceId, Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByIds(scienceId, topicId);
        return questionMapper.mapQuestionListToQuestionDtoList(questions);
    }

    @Transactional(readOnly = true)
    public List<QuestionShortDto> getQuestionsByTopicId(Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByTopicId(topicId);
        return questionMapper.mapQuestionListToQuestionShortDtoList(questions);
    }

    @Transactional(readOnly = true)
    public List<QuestionSaveDto> getQuestionSaveDtoByTopicId(Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByTopicId(topicId);

        List<QuestionSaveDto> questionSaveDtoList = new ArrayList<>();

        for (Question question : questions) {
            questionSaveDtoList.add(
                    QuestionSaveDto.builder()
                            .topicId(question.getTopic().getId())
                            .questionText(question.getQuestionText())
                            .answers(answerMapper.mapAnswerListToAnswerShorDtoList(question.getAnswers()))
                            .build());
        }

        return questionSaveDtoList;
    }

    /*@Transactional(readOnly = true)
    public List<QuestionDto> getQuestionDtoListByTopicId(Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByTopicId(topicId);
        return questionMapper.mapQuestionListToQuestionDtoList(questions);
    }*/

    @Transactional(readOnly = true)
    public boolean isQuestionWithAnswersExists(
            @NotNull List<QuestionShortDto> existingQuestions,
            QuestionShortDto newQuestion
    ) {
        return existingQuestions.stream()
                .filter(q -> q.questionText().equalsIgnoreCase(newQuestion.questionText()))
                .anyMatch(q -> {
                    List<AnswerShortDto> existingAnswers = q.answers();
                    List<AnswerShortDto> newAnswers = newQuestion.answers();

                    if (existingAnswers.size() != newAnswers.size()) {
                        return false; // количество ответов не совпадает
                    }

                    // проверяем, что каждый ответ newQuestion есть в existingAnswers
                    return newAnswers.stream()
                            .allMatch(newAns -> existingAnswers.stream()
                                    .anyMatch(existingAns -> existingAns.answerText().equalsIgnoreCase(newAns.answerText())
                                            && existingAns.isTrue().equals(newAns.isTrue())
                                    )
                            );
                });
    }

    @Transactional(readOnly = true)
    public boolean isQuestionWithAnswersExists(
            @NotNull List<QuestionSaveDto> existingQuestions,
            QuestionSaveDto newQuestion
    ) {
        Set<String> newAnswerSet = newQuestion.answers().stream()
                .map(this::normalizeAnswer)
                .collect(Collectors.toSet());

        return existingQuestions.stream()
                .filter(q -> q.questionText().equalsIgnoreCase(newQuestion.questionText()))
                .anyMatch(q -> {
                    Set<String> existingAnswerSet = q.answers().stream()
                            .map(this::normalizeAnswer)
                            .collect(Collectors.toSet());

                    return existingAnswerSet.equals(newAnswerSet);
                });
    }

    private String normalizeAnswer(AnswerShortDto a) {
        return a.answerText().trim().toLowerCase();
    }

    @Transactional
    public Question saveQuestion(Long topicId, QuestionShortDto newQuestion) {

        validation.textFieldMustNotBeEmpty(newQuestion.questionText());

        Question question = questionMapper.mapQuestionShortDtoToQuestion(newQuestion);

        if (question.getAnswers() != null) {
            for (Answer answer : question.getAnswers()) {

                validation.textFieldMustNotBeEmpty(answer.getAnswerText());
                validation.textFieldMustNotBeEmpty(answer.getCommentary());

                answer.setQuestion(question);
            }
        }
        question.setTopic(topicRepository.findById(topicId).orElse(null));
        return questionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public QuestionDto getQuestionById(Long questionId) {
        Question question = questionRepository.getQuestionById(questionId);
        return questionMapper.mapQuestiontoQuestionDto(question);
    }

    @Transactional
    public void save(QuestionSaveDto questionSaveDto) {
        Topic topic = topicRepository.getTopicById(questionSaveDto.topicId());

        validation.textFieldMustNotBeEmpty(questionSaveDto.questionText());
        Question newQuestion = Question.builder()
                .questionText(questionSaveDto.questionText())
                .topic(topic)
                .build();

        Question savedQuestion = questionRepository.save(newQuestion);

        List<AnswerShortDto> answerShortDtoList = questionSaveDto.answers();

        List<Answer> answerList = answerShortDtoList.stream().
                map(answerShortDto -> {
                    validation.textFieldMustNotBeEmpty(answerShortDto.answerText());
                    validation.textFieldMustNotBeEmpty(answerShortDto.commentary());
                    Answer answer = answerMapper.mapAnswerShortDtoToAnswer(answerShortDto);
                    answer.setQuestion(savedQuestion);
                    return answer;
                }).toList();

        answerRepository.saveAll(answerList);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        questionRepository.delete(question);
    }

    @Transactional
    public void updateQuestion(QuestionDto dto) {
        // 1️⃣ ВАЛИДАЦИЯ (СНАЧАЛА!)
        List<String> answerTextList = dto.answers().stream()
                .map(AnswerDto::answerText)
                .toList();

        validation.textFieldOfListMustNotBeEmpty(answerTextList);

        // 2️⃣ ЗАГРУЗКА
        Question question = questionRepository.findById(dto.id())
                .orElseThrow(() ->
                        new IllegalArgumentException("Savol ma'lumotlar bazasida topilmadi."));

        List<Question> existingQuestions = questionRepository.getQuestionsByTopicId(question.getTopic().getId());

        List<QuestionSaveDto> existingQuestionSaveDtos =
                existingQuestions.stream()
                        .map(q ->
                                QuestionSaveDto.builder()
                                        .topicId(q.getTopic().getId())
                                        .questionText(q.getQuestionText())
                                        .answers(answerMapper.mapAnswerListToAnswerShorDtoList(q.getAnswers()))
                                        .build()
                        ).toList();

        QuestionSaveDto newUpdatingQuestion =
                QuestionSaveDto.builder()
                        .topicId(question.getTopic().getId())
                        .questionText(dto.questionText())
                        .answers(answerMapper.mapAnswerDtoListToAnswerShorDtoList(dto.answers()))
                        .build();

        boolean questionWithAnswersExists = isQuestionWithAnswersExists(existingQuestionSaveDtos, newUpdatingQuestion);
        if (questionWithAnswersExists) {
            throw new IllegalArgumentException("Bunday javoblarga ega savol allaqachon mavjud.");
        }


        // 3️⃣ ОБНОВЛЕНИЕ ВОПРОСА
        validation.textFieldMustNotBeEmpty(dto.questionText().trim());
        question.setQuestionText(dto.questionText().trim());

        // 4️⃣ СБРОС ВСЕХ ОТВЕТОВ
        for (Answer answer : question.getAnswers()) {
            answer.setIsTrue(false);
            answer.setCommentary("noto'g'ri javob");
        }

        // 5️⃣ УСТАНОВКА НОВЫХ ЗНАЧЕНИЙ
        for (AnswerDto aDto : dto.answers()) {

            Answer answer = answerRepository.findById(aDto.id())
                    .orElseThrow(() ->
                            new IllegalArgumentException("Javob ma'lumotlar bazasida topilmadi."));

            answer.setAnswerText(aDto.answerText().trim());
            answer.setIsTrue(aDto.isTrue());

            if (Boolean.TRUE.equals(aDto.isTrue())) {
                validation.textFieldMustNotBeEmpty(aDto.commentary());
                answer.setCommentary(aDto.commentary().trim());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<QuestionDto> getQuestionDtoPageByTopicId(Long topicId, String search, Pageable pageable) {
        Page<Question> page;

        if (search == null || search.isBlank()) {
            page = questionRepository.findByTopicId(topicId, pageable);
        } else {
            page = questionRepository
                    .findByTopicIdAndQuestionTextContainingIgnoreCase(
                            topicId,
                            search,
                            pageable
                    );
        }

        return page.map(question -> questionMapper.mapQuestiontoQuestionDto(question));
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> findAll(Long topicId, String q) {

        List<Question> list;

        if (q == null || q.isBlank()) {
            list = questionRepository.findByTopicId(topicId);
        } else {
            list = questionRepository
                    .findByTopicIdAndQuestionTextContainingIgnoreCase(
                            topicId,
                            q
                    );
        }

        return questionMapper.mapQuestionListToQuestionDtoList(list);
    }
}


