package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.AnswerMapper;
import behzoddev.testproject.mapper.QuestionMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<QuestionDto> getQuestionDtoListByTopicId(Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByTopicId(topicId);
        return questionMapper.mapQuestionListToQuestionDtoList(questions);
    }

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

    @Transactional
    public Question saveQuestion(Long topicId, QuestionShortDto newQuestion) {
        Validation.validateName(newQuestion.questionText().trim());

        Question question = questionMapper.mapQuestionShortDtoToQuestion(newQuestion);

        if (question.getAnswers() != null) {
            for (Answer answer : question.getAnswers()) {
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

        Question newQuestion = Question.builder()
                .questionText(questionSaveDto.questionText())
                .topic(topic)
                .build();

        Question savedQuestion = questionRepository.save(newQuestion);

        List<AnswerShortDto> answerShortDtoList = questionSaveDto.answers();

             List<Answer> answerList = answerShortDtoList.stream().
                map(answerShortDto -> {
                    Answer answer = answerMapper.mapAnswerShortDtoToAnswer(answerShortDto);
                    answer.setQuestion(savedQuestion);
                    return answer;
                }).toList();

        answerRepository.saveAll(answerList);
    }

    public boolean isUnique(List<AnswerShortDto> answerShortDto) {
        Set<String> uniqueAnswers =
                answerShortDto.stream()
                        .map(answers -> answers.answerText().trim().toLowerCase())
                        .collect(Collectors.toSet());

        return uniqueAnswers.size() == answerShortDto.size();
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        questionRepository.delete(question);
    }

    @Transactional
    public void updateQuestion(QuestionDto questionDto) {
        Question question = questionRepository.findById(questionDto.id())
                .orElseThrow(() -> new RuntimeException("Question not found"));

        question.setQuestionText(questionDto.questionText());

        for (AnswerDto answerDto : questionDto.answers()) {

            Answer answer = answerRepository.findById(Math.toIntExact(answerDto.id()))
                    .orElseThrow(() -> new RuntimeException("Answer not found"));

            answer.setAnswerText(answerDto.answerText());
            answer.setIsTrue(answerDto.isTrue());
            answer.setCommentary(answerDto.commentary());
        }
    }





}
