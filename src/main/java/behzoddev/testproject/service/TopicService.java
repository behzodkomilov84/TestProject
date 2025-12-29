package behzoddev.testproject.service;

import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.TopicIdAndNameDto;
import behzoddev.testproject.dto.TopicNameDto;
import behzoddev.testproject.dto.TopicShortDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.TopicMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TopicService {
    private final TopicRepository topicRepository;
    private final TopicMapper topicMapper;
    private final ScienceRepository scienceRepository;

    public Set<TopicIdAndNameDto> getTopicsByScienceId(Long scienceId) {
        return topicRepository.findTopicsByScienceId(scienceId);
    }


    public TopicIdAndNameDto getTopicByIds(Long scienceId, Long topicId) {
        return topicRepository.findTopicByIds(scienceId, topicId);
    }

    @Transactional
    public Topic saveTopic(Long scienceId, TopicNameDto topicNameDto) {
        Validation.validateName(topicNameDto.name().trim());

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

    @Transactional
    public void removeTopic(Long topicId) {
        topicRepository.deleteById(topicId);
    }

    @Transactional
    public Set<TopicShortDto> getTopicShortDtoByScienceId(Long scienceId) {
        return topicRepository.getTopicShortDtoByScienceId(scienceId);
    }

    @Transactional
    public void updateTopic(Long id, String name) {
        topicRepository.updateTopicName(id, name);
    }
}
