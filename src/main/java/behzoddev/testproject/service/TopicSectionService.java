package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.section.TopicSectionCourseTitleDto;
import behzoddev.testproject.dto.section.TopicSectionIdAndNameDto;
import behzoddev.testproject.dto.section.TopicSectionNameDto;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.mapper.TopicSectionMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// "Bo'lim" (TopicSection) CRUD — Fan ichida mavzularni guruhlash uchun.
// TopicService/ScienceService bilan bir xil andoza: oddiy CRUD + tartib
// (orderIndex) — CourseService.reorderSections'dagi qayta tartiblash
// yechimi shu yerda ham qo'llanilgan.
@Service
@RequiredArgsConstructor
public class TopicSectionService {

    private final TopicSectionRepository topicSectionRepository;
    private final TopicRepository topicRepository;
    private final ScienceRepository scienceRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final TopicSectionMapper topicSectionMapper;
    private final Validation validation;

    @Transactional(readOnly = true)
    public List<TopicSectionIdAndNameDto> getSectionsByScienceId(Long scienceId) {
        List<TopicSectionIdAndNameDto> sections = topicSectionRepository.findByScienceIdOrderByOrderIndex(scienceId);

        // Qaysi bo'limlar biror kursga bog'langanini BULK olib, "🔗 Kurs:
        // ..." belgisi uchun nomni qo'shib qo'yamiz (topic-sections
        // sahifasi). Shu maydon bo'limni shu yerdan tahrirlash mumkin/
        // mumkin emasligini ham bildiradi (frontend edit()'da tekshiradi).
        Map<Long, String> courseTitleBySectionId = courseSectionRepository.findLinkedCourseTitlesBySectionScienceId(scienceId)
                .stream()
                .collect(Collectors.toMap(TopicSectionCourseTitleDto::sectionId, TopicSectionCourseTitleDto::courseTitle, (a, b) -> a));

        if (courseTitleBySectionId.isEmpty()) {
            return sections;
        }

        return sections.stream()
                .map(s -> new TopicSectionIdAndNameDto(s.id(), s.name(), s.orderIndex(), courseTitleBySectionId.get(s.id())))
                .toList();
    }

    @Transactional
    public TopicSection saveSection(Long scienceId, TopicSectionNameDto dto) {
        validation.textFieldMustNotBeEmpty(dto.name());

        if (topicSectionRepository.existsByScience_IdAndNameIgnoreCase(scienceId, dto.name().trim())) {
            throw new IllegalArgumentException("❌Bu nomdagi bo'lim allaqachon mavjud.");
        }

        TopicSection section = topicSectionMapper.mapNameDtoToTopicSection(dto);
        section.setName(dto.name().trim());
        section.setScience(scienceRepository.findById(scienceId).orElse(null));

        int nextOrder = topicSectionRepository.findMaxOrderIndexByScienceId(scienceId) != null
                ? topicSectionRepository.findMaxOrderIndexByScienceId(scienceId) + 1
                : 1;
        section.setOrderIndex(nextOrder);

        return topicSectionRepository.save(section);
    }

    @Transactional
    public void updateSectionName(Long id, String name) {
        validation.textFieldMustNotBeEmpty(name);

        TopicSection section = topicSectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("❌Bo'lim topilmadi."));

        // Kursga bog'langan bo'lim (ya'ni ichida kursga bog'langan mavzu
        // bor) shu yerdan (TEST BOSHQARUVI) nomini o'zgartirib bo'lmaydi —
        // uning nomi kurs Bo'limi (CourseChapter) bilan BIR TOMONLAMA
        // sinxronlangan (CourseService.renameChapter ->
        // syncTopicSectionNamesForChapter: kurs -> test boshqaruvi, aksi
        // emas). Shu yerdan qo'lda o'zgartirilsa, keyingi kurs tomonidagi
        // rename'da bu o'zgarish ustidan yozilib, "yo'qolib qolar" edi —
        // shuning uchun ataylab bloklangan, aniq xabar bilan kursga
        // yo'naltiriladi.
        courseSectionRepository.findFirstByLinkedTopic_Section_Id(id).ifPresent(cs -> {
            throw new IllegalArgumentException("❌ Bu bo'lim \"" + cs.getCourse().getTitle() +
                    "\" kursiga bog'langan. Uni faqat shu kurs ichidan (kurs sahifasidagi Bo'lim ✏️ tugmasi orqali) tahrirlashingiz mumkin.");
        });

        section.setName(name.trim());
        topicSectionRepository.save(section);
    }

    // Bo'lim o'chirilganda unga tegishli mavzular O'CHMAYDI — faqat
    // bo'limsiz holatga qaytadi (topics.section_id FK "ON DELETE SET
    // NULL" — sxemada shunga mos qilib yaratilgan).
    @Transactional
    public void removeSection(Long sectionId) {
        topicSectionRepository.deleteById(sectionId);
    }

    // Frontend to'liq tartiblangan id ro'yxatini yuboradi — biz
    // orderIndex'larni 1'dan qayta hisoblaymiz (CourseService.reorderSections
    // bilan bir xil andoza).
    @Transactional
    public void reorderSections(Long scienceId, List<Long> orderedSectionIds) {
        List<TopicSection> sections = topicSectionRepository.findByScience_IdOrderByOrderIndexAsc(scienceId);
        Map<Long, TopicSection> byId = new LinkedHashMap<>();
        for (TopicSection s : sections) {
            byId.put(s.getId(), s);
        }

        if (orderedSectionIds.size() != sections.size() || !byId.keySet().containsAll(orderedSectionIds)) {
            throw new IllegalArgumentException("❌Bo'limlar ro'yxati fanning bo'limlariga mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedSectionIds) {
            byId.get(id).setOrderIndex(index++);
        }
        topicSectionRepository.saveAll(sections);
    }

    // Mavzuni bo'limga biriktirish/bo'shatish (sectionId=null — bo'limsiz
    // qilib qo'yadi). Bulk JPQL update o'rniga entity yuklab-o'zgartirib
    // saqlash ishlatiladi — "t.section = :sectionId" null bilan JPQL'da
    // ishonchsiz ishlaydi, shu sabab ataylab shu yo'l tanlangan.
    @Transactional
    public void assignTopicToSection(Long topicId, Long sectionId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("❌Mavzu topilmadi."));

        // Kursga bog'langan mavzu (ya'ni unga ishora qiluvchi CourseSection
        // bor) — Bo'limini ham shu yerdan (TEST BOSHQARUVI) o'zgartirib
        // bo'lmaydi, xuddi nomini o'zgartirib bo'lmagani kabi
        // (TopicService.updateTopic bilan bir xil qoida, foydalanuvchi
        // so'rovi bo'yicha: bunday mavzu FAQAT kurs ichidan tahrirlanadi —
        // qisman ham emas).
        courseSectionRepository.findByLinkedTopic_Id(topicId).ifPresent(cs -> {
            throw new IllegalArgumentException("❌ Bu mavzu \"" + cs.getCourse().getTitle() +
                    "\" kursiga bog'langan. Uni faqat shu kurs ichidan (kurs sahifasidagi mavzu ✏️ tugmasi orqali) tahrirlashingiz mumkin.");
        });

        if (sectionId == null) {
            topic.setSection(null);
        } else {
            TopicSection section = topicSectionRepository.findById(sectionId)
                    .orElseThrow(() -> new IllegalArgumentException("❌Bo'lim topilmadi."));
            topic.setSection(section);
        }
        topicRepository.save(topic);
    }
}
