package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.topic.TopicCourseLinkDto;
import behzoddev.testproject.dto.topic.TopicCourseTitleDto;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        List<TopicIdAndNameDto> topics = topicRepository.findTopicsByScienceId(scienceId);

        // Shu fandagi qaysi mavzular kurs bo'limiga bog'langanini BULK
        // olib, har bir mavzuga mos "🔗 Kurs: ..." belgisi uchun nomni
        // qo'shib qo'yamiz (topics.html). Bir xil topicId ikki marta
        // uchrasa (nazariy jihatdan — bir mavzu bir nechta kurs bo'limiga
        // bog'langan bo'lsa), birinchisi olinadi.
        Map<Long, String> courseTitleByTopicId = courseSectionRepository.findLinkedCourseTitlesByScienceId(scienceId)
                .stream()
                .collect(Collectors.toMap(TopicCourseTitleDto::topicId, TopicCourseTitleDto::courseTitle, (a, b) -> a));

        if (courseTitleByTopicId.isEmpty()) {
            return topics;
        }

        return topics.stream()
                .map(t -> new TopicIdAndNameDto(t.id(), t.name(), t.sectionId(), courseTitleByTopicId.get(t.id())))
                .toList();
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

        // MUHIM: TopicController.saveTopic HAR DOIM shu metodni chaqiradi —
        // hatto foydalanuvchi faqat mavzuning Bo'limini (sectionId)
        // o'zgartirgan, nomiga tegmagan bo'lsa ham (chunki "updated"
        // ro'yxati ikkalasidan BIRI o'zgarsa ham to'ldiriladi, topic.js).
        // Shu sabab bloklash faqat NOM HAQIQATAN o'zgarayotganda ishga
        // tushishi kerak — aks holda kursga bog'langan mavzuning Bo'limini
        // qayta biriktirish (nomini tegmasdan) ham bloklanib qolar edi,
        // bu so'ralmagan.
        Topic existing = topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("❌Mavzu topilmadi."));

        if (!existing.getName().equals(name)) {
            // Kursga bog'langan mavzu (ya'ni unga ishora qiluvchi
            // CourseSection bor) shu yerdan (TEST BOSHQARUVI) nomini
            // o'zgartirib bo'lmaydi — TopicSectionService.updateSectionName
            //'dagi Bo'lim uchun bilan bir xil qoida: kursga bog'langan
            // narsalar faqat kurs ichidan tahrirlanishi kerak, ikkala
            // tomonda alohida-alohida o'zgartirilib, chalkashib
            // qolmasligi uchun.
            courseSectionRepository.findByLinkedTopic_Id(id).ifPresent(cs -> {
                throw new IllegalArgumentException("❌ Bu mavzu \"" + cs.getCourse().getTitle() +
                        "\" kursiga bog'langan. Uni faqat shu kurs ichidan (kurs sahifasidagi mavzu ✏️ tugmasi orqali) tahrirlashingiz mumkin.");
            });
        }

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
