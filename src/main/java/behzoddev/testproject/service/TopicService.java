package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.topic.TopicCourseLinkDto;
import behzoddev.testproject.dto.topic.TopicCourseTitleDto;
import behzoddev.testproject.dto.topic.TopicIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicNameDto;
import behzoddev.testproject.dto.topic.TopicTrashDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.mapper.TopicMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicService {
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
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
                .map(t -> new TopicIdAndNameDto(t.id(), t.name(), t.sectionId(),
                        courseTitleByTopicId.get(t.id()), t.questionCount()))
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

    // "O'chirilganlar savati"ga o'tkazish (soft-delete) — DARHOL butunlay
    // o'chirilmaydi, savollari (Question) HAM tegilmay saqlanadi —
    // "♻️ Tiklash" bilan bir zumda qaytadi (CourseService.deleteCourse
    // bilan bir xil g'oya).
    @Transactional
    public void removeTopic(Long topicId) {
        Topic topic = getTopicOrThrow(topicId);
        topic.setDeletedAt(LocalDateTime.now());
        topicRepository.save(topic);
    }

    // "🗑️ Testi yo'q mavzularni o'chirish" — shu Fanda hech qanday savoli
    // bo'lmagan (questionCount==0) BARCHA mavzularni bir yo'la (soft-delete)
    // o'chiradi. Kursga bog'langan mavzular ATAYLAB chetlab o'tiladi —
    // kursga bog'langan mavzuni shu yerdan o'chirish umuman mumkin emas
    // (TopicService.updateTopic'dagi bilan bir xil qoida — faqat kurs
    // ichidan boshqariladi).
    @Transactional
    public int deleteQuestionlessTopics(Long scienceId) {
        Set<Long> linkedTopicIds = courseSectionRepository.findLinkedCourseTitlesByScienceId(scienceId)
                .stream()
                .map(TopicCourseTitleDto::topicId)
                .collect(Collectors.toSet());

        List<Long> deletableIds = topicRepository.findTopicsByScienceId(scienceId).stream()
                .filter(t -> t.questionCount() == 0 && !linkedTopicIds.contains(t.id()))
                .map(TopicIdAndNameDto::id)
                .toList();

        if (!deletableIds.isEmpty()) {
            List<Topic> deletable = topicRepository.findAllById(deletableIds);
            LocalDateTime now = LocalDateTime.now();
            deletable.forEach(t -> t.setDeletedAt(now));
            topicRepository.saveAll(deletable);
        }
        return deletableIds.size();
    }

    // "O'chirilganlar savati" ro'yxati (Fan ichida).
    @Transactional(readOnly = true)
    public List<TopicTrashDto> getDeletedTopics(Long scienceId) {
        return topicRepository.findDeletedByScienceId(scienceId);
    }

    // "♻️ Tiklash" — mavzuni savatdan qaytaradi, savollari avtomatik yana
    // ko'rinadigan bo'ladi (ular hech qachon o'chirilmagan edi).
    @Transactional
    public void restoreTopic(Long topicId) {
        Topic topic = getAnyTopicOrThrow(topicId);
        if (topic.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu mavzu o'chirilmagan — tiklashning hojati yo'q.");
        }
        topic.setDeletedAt(null);
        topicRepository.save(topic);
    }

    // "🗑️ Butunlay o'chirish" — FAQAT allaqachon savatda turgan mavzuga
    // nisbatan. QAYTARIB BO'LMAYDI: savollar (questions.topic_id'da FK
    // yo'qligi uchun) ANIQ, alohida o'chiriladi (aks holda "egasiz" bo'lib
    // qolib ketardi).
    @Transactional
    public void permanentlyDeleteTopic(Long topicId) {
        Topic topic = getAnyTopicOrThrow(topicId);
        if (topic.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu mavzuni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
        }
        questionRepository.deleteByTopic_Id(topicId);
        topicRepository.delete(topic);
    }

    private Topic getTopicOrThrow(Long topicId) {
        Topic topic = getAnyTopicOrThrow(topicId);
        if (topic.getDeletedAt() != null) {
            throw new NoSuchElementException("Mavzu topilmadi");
        }
        return topic;
    }

    // FAQAT "O'chirilganlar savati" amallari (restoreTopic,
    // permanentlyDeleteTopic, getDeletedTopics) uchun — soft-delete
    // qilingan mavzuni ham topa oladi.
    private Topic getAnyTopicOrThrow(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new NoSuchElementException("Mavzu topilmadi"));
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

    // Frontend to'liq tartiblangan id ro'yxatini yuboradi (⬆⬇ yoki A-Z/Z-A
    // saralashdan keyin) — biz orderIndex'larni 1'dan qayta hisoblaymiz
    // (TopicSectionService.reorderSections bilan bir xil andoza).
    @Transactional
    public void reorderTopics(Long scienceId, List<Long> orderedTopicIds) {
        List<Topic> topics = topicRepository.findByScience_IdAndDeletedAtIsNullOrderByOrderIndexAsc(scienceId);
        Map<Long, Topic> byId = new LinkedHashMap<>();
        for (Topic t : topics) {
            byId.put(t.getId(), t);
        }

        if (orderedTopicIds.size() != topics.size() || !byId.keySet().containsAll(orderedTopicIds)) {
            throw new IllegalArgumentException("❌Mavzular ro'yxati fanning mavzulariga mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedTopicIds) {
            byId.get(id).setOrderIndex(index++);
        }
        topicRepository.saveAll(topics);
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
