package behzoddev.testproject.service;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.mapper.QuestionMapper;
import behzoddev.testproject.mapper.ScienceMapper;
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
    private final QuestionMapper questionMapper;

    @Transactional(readOnly = true)
    public Set<ScienceDto> getAllSciencesDto() {
        Set<Science> scienceWithTopics = scienceRepository.findAllWithTopics();

        return scienceMapper.toScinceDtoSet(scienceWithTopics);
    }

    @Transactional(readOnly = true)
    public Set<ScienceNameDto> getAllScienceNameDto() {
        return scienceRepository.findAllScienceNames();
    }

    @Transactional(readOnly = true)
    public Optional<ScienceDto> getScienceById(Long id) {
        return scienceRepository.findByIdWithTopics(id).map(scienceMapper::mapSciencetoScienceDto);
    }

    public Optional<ScienceNameDto> getScienceNameById(Long id) {
        return scienceRepository.findScienceNameById(id);
    }

    public Set<TopicNameDto> getTopicsByScienceId(Long id) {
        return topicRepository.findTopicsByScienceId(id);
    }

    public TopicNameDto getTopicByIds(Long scienceId, Long topicId) {
        return topicRepository.findTopicByIds(scienceId, topicId);
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestionsByIds(Long scienceId, Long topicId) {
        List<Question> questions =  questionRepository.getQuestionsByIds(scienceId, topicId);
        return questionMapper.mapQuestionListToQuestionDtoList(questions);
    }
}
