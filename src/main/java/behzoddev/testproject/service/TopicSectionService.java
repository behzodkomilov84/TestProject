package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.section.SectionTopicCountDto;
import behzoddev.testproject.dto.section.TopicSectionCourseTitleDto;
import behzoddev.testproject.dto.section.TopicSectionIdAndNameDto;
import behzoddev.testproject.dto.section.TopicSectionNameDto;
import behzoddev.testproject.dto.section.TopicSectionTrashDto;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.TopicSectionMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
    private final CourseSectionRepository courseSectionRepository;
    private final TopicSectionMapper topicSectionMapper;
    private final Validation validation;
    private final ScienceService scienceService;

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "TEST
    // BOSHQARUVI dagi barcha N+1 so'rov muammolarini ko'rib chiq") —
    // ilgari topicSectionRepository.findByScienceIdOrderByOrderIndex()
    // ishlatilardi, u HAR BIR Bo'lim uchun korrelyatsiyalangan subso'rov
    // bajarardi (mavzular soni). TopicService.getTopicsByScienceId'dagi
    // bilan bir xil yechim: yengil (subso'rovsiz) so'rov + ALOHIDA, BULK
    // (GROUP BY) mavzular soni.
    @Transactional(readOnly = true)
    public List<TopicSectionIdAndNameDto> getSectionsByScienceId(Long scienceId) {
        List<TopicSectionIdAndNameDto> sections = topicSectionRepository.findSectionBasicsByScienceId(scienceId);
        if (sections.isEmpty()) {
            return sections;
        }

        Map<Long, Long> topicCounts = topicRepository
                .countBySectionIdsGrouped(sections.stream().map(TopicSectionIdAndNameDto::id).toList())
                .stream()
                .collect(Collectors.toMap(SectionTopicCountDto::sectionId, SectionTopicCountDto::count));

        // Qaysi bo'limlar biror kursga bog'langanini BULK olib, "🔗 Kurs:
        // ..." belgisi uchun nomni qo'shib qo'yamiz (topic-sections
        // sahifasi). Shu maydon bo'limni shu yerdan tahrirlash mumkin/
        // mumkin emasligini ham bildiradi (frontend edit()'da tekshiradi).
        Map<Long, String> courseTitleBySectionId = courseSectionRepository.findLinkedCourseTitlesBySectionScienceId(scienceId)
                .stream()
                .collect(Collectors.toMap(TopicSectionCourseTitleDto::sectionId, TopicSectionCourseTitleDto::courseTitle, (a, b) -> a));

        return sections.stream()
                .map(s -> new TopicSectionIdAndNameDto(s.id(), s.name(), s.orderIndex(),
                        courseTitleBySectionId.get(s.id()), topicCounts.getOrDefault(s.id(), 0L)))
                .toList();
    }

    @Transactional
    public TopicSection saveSection(Long scienceId, TopicSectionNameDto dto, User currentUser) {
        validation.textFieldMustNotBeEmpty(dto.name());
        Science science = scienceService.requireManageableScience(scienceId, currentUser);

        if (topicSectionRepository.existsByScience_IdAndNameIgnoreCase(scienceId, dto.name().trim())) {
            throw new IllegalArgumentException("❌Bu nomdagi bo'lim allaqachon mavjud.");
        }

        TopicSection section = topicSectionMapper.mapNameDtoToTopicSection(dto);
        section.setName(dto.name().trim());
        section.setScience(science);

        int nextOrder = topicSectionRepository.findMaxOrderIndexByScienceId(scienceId) != null
                ? topicSectionRepository.findMaxOrderIndexByScienceId(scienceId) + 1
                : 1;
        section.setOrderIndex(nextOrder);

        return topicSectionRepository.save(section);
    }

    @Transactional
    public void updateSectionName(Long id, String name, User currentUser) {
        validation.textFieldMustNotBeEmpty(name);

        TopicSection section = topicSectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("❌Bo'lim topilmadi."));
        scienceService.checkCanManage(section.getScience(), currentUser);

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

    // "O'chirilganlar savati"ga o'tkazish (soft-delete) — DARHOL butunlay
    // o'chirilmaydi, mavzulari HAM tegilmay saqlanadi — "♻️ Tiklash"
    // bilan bir zumda qaytadi.
    @Transactional
    public void removeSection(Long sectionId, User currentUser) {
        TopicSection section = getSectionOrThrow(sectionId);
        scienceService.checkCanManage(section.getScience(), currentUser);
        section.setDeletedAt(LocalDateTime.now());
        topicSectionRepository.save(section);
    }

    // "O'chirilganlar savati" ro'yxati (Fan ichida).
    @Transactional(readOnly = true)
    public List<TopicSectionTrashDto> getDeletedSections(Long scienceId) {
        return topicSectionRepository.findDeletedByScienceId(scienceId);
    }

    // "♻️ Tiklash" — Bo'limni savatdan qaytaradi, mavzulari avtomatik
    // yana ko'rinadigan bo'ladi (ular hech qachon o'chirilmagan edi).
    @Transactional
    public void restoreSection(Long sectionId, User currentUser) {
        TopicSection section = getAnySectionOrThrow(sectionId);
        scienceService.checkCanManage(section.getScience(), currentUser);
        if (section.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu bo'lim o'chirilmagan — tiklashning hojati yo'q.");
        }
        section.setDeletedAt(null);
        topicSectionRepository.save(section);
    }

    // "🗑️ Butunlay o'chirish" — FAQAT allaqachon savatda turgan Bo'limga
    // nisbatan. QAYTARIB BO'LMAYDI: mavzular O'ZI o'chmaydi (topics.
    // section_id FK "ON DELETE SET NULL" — bo'limsiz bo'lib qoladi).
    @Transactional
    public void permanentlyDeleteSection(Long sectionId, User currentUser) {
        TopicSection section = getAnySectionOrThrow(sectionId);
        scienceService.checkCanManage(section.getScience(), currentUser);
        if (section.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu bo'limni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
        }
        topicSectionRepository.delete(section);
    }

    private TopicSection getSectionOrThrow(Long sectionId) {
        TopicSection section = getAnySectionOrThrow(sectionId);
        if (section.getDeletedAt() != null) {
            throw new NoSuchElementException("Bo'lim topilmadi");
        }
        return section;
    }

    // FAQAT "O'chirilganlar savati" amallari (restoreSection,
    // permanentlyDeleteSection, getDeletedSections) uchun — soft-delete
    // qilingan Bo'limni ham topa oladi.
    private TopicSection getAnySectionOrThrow(Long sectionId) {
        return topicSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Bo'lim topilmadi"));
    }

    // "🗑️ Bo'lim + mavzularni birga o'chirish" — ATAYLAB shu yerda EMAS.
    // Foydalanuvchi so'rovi bo'yicha bu amal FAQAT kurs ichidan
    // (CourseService.deleteChapterWithLinkedTopics, courseDetail.js)
    // bajarilishi mumkin — TEST BOSHQARUVIdan alohida chaqirib
    // bo'lmaydi, chunki bu amal aynan KURS Bo'limi kontekstida ma'noga
    // ega (qaysi Bo'lim mavzulari o'chirilayotgani kurs Bo'limiga
    // qarab aniqlanadi).

    // "🗑️ Bo'sh bo'limlarni o'chirish" tugmasi — shu FANDA hech qanday
    // mavzuga biriktirilmagan (topicCount==0) BARCHA bo'limlarni BIR
    // YO'LA o'chiradi. Kursga bog'lanish xavfsizligi haqida qo'shimcha
    // tekshiruv shart emas — mavzu bo'lmasa (topicCount==0), demak shu
    // bo'lim orqali hech qanday mavzu kursga ham bog'lanmagan bo'ladi.
    @Transactional
    public int deleteEmptySections(Long scienceId, User currentUser) {
        scienceService.requireManageableScience(scienceId, currentUser);

        // findByScienceIdOrderByOrderIndex() o'rniga findSectionBasicsByScienceId()
        // + bulk (GROUP BY) mavzular soni — getSectionsByScienceId()
        // bilan bir xil sabab (HAQIQIY TOPILGAN BUG, 2026-09-12). GROUP
        // BY natijasida FAQAT kamida 1 ta mavzusi bor bo'limlar qaytadi
        // — shu sabab "topicCount == 0" endi "sectionIdsWithTopics'da
        // UMUMAN yo'q" bilan tengma-teng.
        List<TopicSectionIdAndNameDto> basics = topicSectionRepository.findSectionBasicsByScienceId(scienceId);
        Set<Long> sectionIdsWithTopics = basics.isEmpty() ? Set.of() : topicRepository
                .countBySectionIdsGrouped(basics.stream().map(TopicSectionIdAndNameDto::id).toList())
                .stream()
                .map(SectionTopicCountDto::sectionId)
                .collect(Collectors.toSet());

        List<Long> emptyIds = basics.stream()
                .filter(s -> !sectionIdsWithTopics.contains(s.id()))
                .map(TopicSectionIdAndNameDto::id)
                .toList();

        if (!emptyIds.isEmpty()) {
            List<TopicSection> empty = topicSectionRepository.findAllById(emptyIds);
            LocalDateTime now = LocalDateTime.now();
            empty.forEach(s -> s.setDeletedAt(now));
            topicSectionRepository.saveAll(empty);
        }
        return emptyIds.size();
    }

    // Frontend to'liq tartiblangan id ro'yxatini yuboradi — biz
    // orderIndex'larni 1'dan qayta hisoblaymiz (CourseService.reorderSections
    // bilan bir xil andoza).
    @Transactional
    public void reorderSections(Long scienceId, List<Long> orderedSectionIds, User currentUser) {
        scienceService.requireManageableScience(scienceId, currentUser);

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
    public void assignTopicToSection(Long topicId, Long sectionId, User currentUser) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("❌Mavzu topilmadi."));
        scienceService.checkCanManage(topic.getScience(), currentUser);

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
