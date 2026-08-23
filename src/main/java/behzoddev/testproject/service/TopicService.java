package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.topic.TopicCourseLinkDto;
import behzoddev.testproject.dto.topic.TopicIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicNameDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.mapper.TopicMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TopicService {
    private final TopicRepository topicRepository;
    private final TopicMapper topicMapper;
    private final ScienceRepository scienceRepository;
    private final TopicSectionRepository topicSectionRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final Validation validation;

    public List<TopicIdAndNameDto> getTopicsByScienceId(Long scienceId) {
        return topicRepository.findTopicsByScienceId(scienceId);
    }

    public TopicIdAndNameDto getTopicByIds(Long scienceId, Long topicId) {
        return topicRepository.findTopicByIds(scienceId, topicId);
    }

    @Transactional
    public Topic saveTopic(Long scienceId, TopicNameDto topicNameDto) {
        return saveTopic(scienceId, topicNameDto, null);
    }

    // sectionId — ixtiyoriy, yangi mavzu darhol shu Bo'limga biriktiriladi
    // (topics.html'dagi Bo'lim tanlash dropdown'idan keladi). NULL —
    // bo'limsiz (eski xulq-atvor).
    @Transactional
    public Topic saveTopic(Long scienceId, TopicNameDto topicNameDto, Long sectionId) {
        validation.textFieldMustNotBeEmpty(topicNameDto.name());

        Topic topic = topicMapper.mapTopicNameDtoToTopic(topicNameDto);

        if (topic.getQuestions() != null) {
            for (Question question : topic.getQuestions()) {
                question.setTopic(topic);
            }
        }
        topic.setScience(scienceRepository.findById(scienceId).orElse(null));

        if (sectionId != null) {
            TopicSection section = topicSectionRepository.findById(sectionId).orElse(null);
            topic.setSection(section);
        }

        Integer maxOrder = topicRepository.findMaxOrderIndexByScienceId(scienceId);
        topic.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);

        return topicRepository.save(topic);
    }

    @Transactional
    public void removeTopic(Long topicId) {
        topicRepository.deleteById(topicId);
    }

    @Transactional
    public void updateTopic(Long id, String name) {

        validation.textFieldMustNotBeEmpty(name);

        topicRepository.updateTopicName(id, name);
    }

    @Transactional
    public List<TopicWithQuestionCountDto> getTopicsWithQuestionCount(Long scienceId) {
        return topicRepository.getTopicsWithQuestionCount(scienceId);
    }

    // Test yaratish formasidagi "🔗 Mavzuga havola qo'shish" tugmasi uchun —
    // shu mavzuga bog'langan kurs bo'limi bo'lmasa, bo'sh Optional qaytadi
    // (tugma frontendda shunda yashiriladi/o'chirilgan holatda qoladi).
    @Transactional(readOnly = true)
    public Optional<TopicCourseLinkDto> getCourseLinkForTopic(Long topicId) {
        return courseSectionRepository.findByLinkedTopic_Id(topicId)
                .map(section -> new TopicCourseLinkDto(
                        section.getCourse().getId(),
                        section.getId(),
                        section.getLinkedTopic().getName()));
    }
}
