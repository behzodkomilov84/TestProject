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
import behzoddev.testproject.validation.Validation;
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

    @Transactional
    public Science saveScience(ScienceNameDto scienceNameDto) {

        Validation.validateName(scienceNameDto.name().trim());

        if (scienceRepository.existsByName(scienceNameDto.name())) {
            throw new IllegalArgumentException("Science with this name already exists");
        }

        Science science = scienceMapper.mapScienceNameDtoToScience(scienceNameDto);

        // === УСТАНОВКА СВЯЗЕЙ (ВАЖНО) ===
        if (science.getTopics() != null) {
            for (Topic topic : science.getTopics()) {
                topic.setScience(science);

               /* if (topic.getQuestions() != null) {
                    for (Question question : topic.getQuestions()) {
                        question.setTopic(topic);

                        if (question.getAnswers() != null) {
                            for (Answer answer : question.getAnswers()) {
                                answer.setQuestion(question);
                            }
                        }
                    }
                }*/
            }
        }

        return scienceRepository.save(science);
    }

    @Transactional
    public Science saveScience(Science science) {
        Validation.validateName(science.getName().trim());

        return scienceRepository.save(science);
    }

    public Optional<Science> getByName(String scienceName) {
        return scienceRepository.findByName(scienceName);
    }

    public Long getScienceIdByTopicId(Long topicId) {
        return topicRepository.getScienceIdByTopicId(topicId);
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
    public void updateScienceName(Long id, String name) {
        Validation.validateName(name.trim());

        scienceRepository.updateScienceName(id, name);
    }

}