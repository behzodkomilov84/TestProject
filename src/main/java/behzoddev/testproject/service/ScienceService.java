package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.QuestionMapper;
import behzoddev.testproject.mapper.ScienceMapper;
import behzoddev.testproject.mapper.TopicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScienceService {

    private final ScienceRepository scienceRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final ScienceMapper scienceMapper;
    private final TopicMapper topicMapper;
    private final QuestionMapper questionMapper;

    @Transactional(readOnly = true)
    public Set<ScienceDto> getAllSciencesDto() {
        Set<Science> scienceWithTopics = scienceRepository.findAllWithTopics();

        return scienceMapper.toScinceDtoSet(scienceWithTopics);
    }

    @Transactional(readOnly = true)
    public Set<ScienceIdAndNameDto> getAllScienceIdAndNameDto() {
        return scienceRepository.findAllScienceNames();
    }

    @Transactional(readOnly = true)
    public Optional<ScienceDto> getScienceById(Long id) {
        return scienceRepository.findByIdWithTopics(id).map(scienceMapper::mapSciencetoScienceDto);
    }

    public Optional<ScienceIdAndNameDto> getScienceNameById(Long id) {
        return scienceRepository.findScienceNameById(id);
    }

    public Set<TopicIdAndNameDto> getTopicsByScienceId(Long scienceId) {
        return topicRepository.findTopicsByScienceId(scienceId);
    }

    public TopicIdAndNameDto getTopicByIds(Long scienceId, Long topicId) {
        return topicRepository.findTopicByIds(scienceId, topicId);
    }

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

    @Transactional
    public Science saveScience(ScienceNameDto scienceNameDto) {

        if (scienceRepository.existsByName(scienceNameDto.name())) {
            throw new IllegalArgumentException("Science with this name already exists");
        }

        Science science = scienceMapper.mapScienceNameDtoToScience(scienceNameDto);

        // === УСТАНОВКА СВЯЗЕЙ (ВАЖНО) ===
        if (science.getTopics() != null) {
            for (Topic topic : science.getTopics()) {
                topic.setScience(science);

                if (topic.getQuestions() != null) {
                    for (Question question : topic.getQuestions()) {
                        question.setTopic(topic);

                        if (question.getAnswers() != null) {
                            for (Answer answer : question.getAnswers()) {
                                answer.setQuestion(question);
                            }
                        }
                    }
                }
            }
        }

        return scienceRepository.save(science);
    }

    @Transactional
    public Science saveScience(Science science) {
        return scienceRepository.save(science);
    }

    public Optional<Science> getByName(String scienceName) {
        return scienceRepository.findByName(scienceName);
    }

    @Transactional
    public Topic saveTopic(Long scienceId, TopicNameDto topicNameDto) {
        Topic topic = topicMapper.mapTopicNameDtoToTopic(topicNameDto);

        if (topic.getQuestions() != null) {
            for (Question question : topic.getQuestions()) {
                question.setTopic(topic);

                if (question.getAnswers() != null) {
                    for (Answer answer : question.getAnswers()) {
                        answer.setQuestion(question);
                    }
                }
            }
        }
        topic.setScience(scienceRepository.findById(scienceId).orElse(null));
        return topicRepository.save(topic);
    }

    public boolean isQuestionWithAnswersExists(
            List<QuestionShortDto> existingQuestions,
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
                                            && existingAns.isTrue() == newAns.isTrue()
                                    )
                            );
                });
    }

    @Transactional
    public Question saveQuestion(Long topicId, QuestionShortDto newQuestion) {

        Question question = questionMapper.mapQuestionShortDtoToQuestion(newQuestion);

        if (question.getAnswers() != null) {
            for (Answer answer : question.getAnswers()) {
                answer.setQuestion(question);
            }
        }
        question.setTopic(topicRepository.findById(topicId).orElse(null));
        return questionRepository.save(question);
    }

    public Long getScienceIdByTopicId(Long topicId) {
        return topicRepository.getScienceIdByTopicId(topicId);
    }

    @Transactional(readOnly = true)
    public QuestionDto getQuestionById(Long questionId) {
        Question question = questionRepository.getQuestionById(questionId);
        return questionMapper.mapQuestiontoQuestionDto(question);
    }

    @Transactional(readOnly = true)
    public boolean isScienceIdExist(Long scienceId) {
        return scienceRepository.existsById(scienceId);
    }

    @Transactional(readOnly = true)
    public boolean isScienceNameExist(String scienceName) {
        Optional<Science> science = getByName(scienceName);
        return science.isPresent();
    }

    @Transactional
    public void removeScience(Long scienceId) {
        scienceRepository.deleteById(scienceId);
    }

    @Transactional
    public void removeTopic(Long topicId) {
        topicRepository.deleteById(topicId);
    }

    @Transactional
    public void removeQuestion(Long questionId) {
        questionRepository.deleteById(questionId);
    }

    @Transactional
    public void updateScienceName(Long id, String name) {
        scienceRepository.updateScienceName(id, name);
    }

}