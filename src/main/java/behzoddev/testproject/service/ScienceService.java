package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.science.ScienceDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.science.ScienceNameDto;
import behzoddev.testproject.dto.science.ScienceTrashDto;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.ScienceMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScienceService {

    private final ScienceRepository scienceRepository;
    private final TopicRepository topicRepository;
    private final TopicSectionRepository topicSectionRepository;
    private final CourseFieldRepository courseFieldRepository;
    private final ScienceMapper scienceMapper;
    private final Validation validation;

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

    // ADMIN cheklovi: fanni yaratgan foydalanuvchi uning egasi bo'ladi —
    // keyinchalik shu fan (va ichidagi Bo'lim/Mavzu/Savollar) FAQAT o'sha
    // ADMIN (yoki cheklovsiz OWNER) tomonidan boshqarilishi mumkin
    // (foydalanuvchi so'rovi, 2026-09-08: "ROLE_ADMIN o'zi yaratmagan
    // hech qaysi joyda o'zgartirish qila olmasin. Kursda, test
    // boshqaruvida..." — CourseService.createdBy bilan bir xil g'oya).
    @Transactional
    public Science saveScience(ScienceNameDto scienceNameDto, User currentUser) {

        validation.textFieldMustNotBeEmpty(scienceNameDto.name());

        if (scienceRepository.existsByName(scienceNameDto.name())) {
            throw new IllegalArgumentException("Bunday nomli fan allaqachon mavjud");
        }

        Science science = scienceMapper.mapScienceNameDtoToScience(scienceNameDto);

        // === УСТАНОВКА СВЯЗЕЙ (ВАЖНО) ===
        if (science.getTopics() != null) {
            for (Topic topic : science.getTopics()) {
                topic.setScience(science);
            }
        }

        if (scienceNameDto.fieldId() != null) {
            science.setField(getFieldOrThrow(scienceNameDto.fieldId()));
        }

        science.setCreatedBy(currentUser);

        Integer maxOrder = scienceRepository.findMaxOrderIndex();
        science.setOrderIndex(maxOrder != null ? maxOrder + 1 : 1);

        return scienceRepository.save(science);
    }

    // "🔀 Yo'nalishga biriktirish" — science.js'da Bo'lim (Science)
    // tahrirlanayotganda Yo'nalish select'i o'zgartirilsa, darhol (batch
    // "Save to DB"dan mustaqil) saqlanadi (courseDetail.js'dagi Mavzu
    // (chapter) tanlashdan farqli — bu yerda alohida, sodda API). fieldId
    // null bo'lsa — Yo'nalishdan chiqariladi (unlink).
    @Transactional
    public void assignField(Long scienceId, Long fieldId, User currentUser) {
        Science science = getScienceOrThrow(scienceId);
        checkCanManage(science, currentUser);
        science.setField(fieldId != null ? getFieldOrThrow(fieldId) : null);
        scienceRepository.save(science);
    }

    // CourseService.canManageCourse/checkCanManage bilan bir xil qoida:
    // ROLE_OWNER — cheklovsiz, ROLE_ADMIN — faqat o'zi yaratgan fan.
    public boolean canManageScience(Science science, User user) {
        return user.hasRole("ROLE_OWNER")
                || (science.getCreatedBy() != null && science.getCreatedBy().getId().equals(user.getId()));
    }

    public void checkCanManage(Science science, User user) {
        if (!canManageScience(science, user)) {
            throw new AccessDeniedException("⛔ Faqat o'zingiz yaratgan fanni (va uning Bo'lim/Mavzu/Savollarini) boshqarishingiz mumkin.");
        }
    }

    // getScienceOrThrow + checkCanManage — TopicService/TopicSectionService/
    // QuestionService/QuestionController/TopicController/TopicSectionController
    // shu orqali boshqarish huquqini tekshiradi (CourseService.
    // requireManageableCourse bilan bir xil andoza).
    @Transactional(readOnly = true)
    public Science requireManageableScience(Long scienceId, User currentUser) {
        Science science = getScienceOrThrow(scienceId);
        checkCanManage(science, currentUser);
        return science;
    }

    // Quyidagi ikkitasi — ExcelImportController kabi "topicId"/"sectionId"
    // orqali ishlaydigan (Science'ni bevosita bilmaydigan) joylar uchun
    // qulaylik: Mavzu/Bo'limdan Fanni topib, xuddi shu tekshiruvni
    // qo'llaydi (import/eksport — foydalanuvchi so'rovi, 2026-09-08:
    // "ROLE_ADMIN o'zi yaratmagan kursga/fanga oid ma'lumotlarni excel
    // word'ga eksport qila olmasin").
    @Transactional(readOnly = true)
    public void checkCanManageByTopicId(Long topicId, User currentUser) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new NoSuchElementException("Mavzu topilmadi"));
        checkCanManage(topic.getScience(), currentUser);
    }

    @Transactional(readOnly = true)
    public void checkCanManageBySectionId(Long sectionId, User currentUser) {
        TopicSection section = topicSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Bo'lim topilmadi"));
        checkCanManage(section.getScience(), currentUser);
    }

    private CourseField getFieldOrThrow(Long fieldId) {
        return courseFieldRepository.findById(fieldId)
                .filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Yo'nalish topilmadi"));
    }

    @Transactional
    public Science saveScience(Science science, User currentUser) {
        validation.textFieldMustNotBeEmpty(science.getName());
        checkCanManage(getScienceOrThrow(science.getId()), currentUser);

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

    // "O'chirilganlar savati"ga o'tkazish (soft-delete) — DARHOL butunlay
    // o'chirilmaydi, Bo'lim/mavzu/savollari HAM tegilmay saqlanadi —
    // "♻️ Tiklash" bilan bir zumda qaytadi (CourseService.deleteCourse
    // bilan bir xil g'oya).
    @Transactional
    public void removeScience(Long scienceId, User currentUser) {
        Science science = getScienceOrThrow(scienceId);
        checkCanManage(science, currentUser);
        science.setDeletedAt(LocalDateTime.now());
        scienceRepository.save(science);
    }

    // "O'chirilganlar savati" ro'yxati.
    @Transactional(readOnly = true)
    public List<ScienceTrashDto> getDeletedSciences() {
        return scienceRepository.findAllDeleted();
    }

    // "♻️ Tiklash" — fanni savatdan qaytaradi, Bo'lim/mavzu/savollari
    // avtomatik yana ko'rinadigan bo'ladi (ular hech qachon o'chirilmagan edi).
    @Transactional
    public void restoreScience(Long scienceId, User currentUser) {
        Science science = getAnyScienceOrThrow(scienceId);
        checkCanManage(science, currentUser);
        if (science.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu fan o'chirilmagan — tiklashning hojati yo'q.");
        }
        science.setDeletedAt(null);
        scienceRepository.save(science);
    }

    // "🗑️ Butunlay o'chirish" — FAQAT allaqachon savatda turgan fanga
    // nisbatan. QAYTARIB BO'LMAYDI. Bo'lim/mavzular hali mavjud bo'lsa —
    // FK RESTRICT (topics.science_id) tufayli xato beradi (foydalanuvchi
    // avval ularni o'chirishi kerak) — GlobalRestExceptionHandler buni
    // tushunarli "bog'liq ma'lumotlar mavjud" xabariga aylantiradi.
    @Transactional
    public void permanentlyDeleteScience(Long scienceId, User currentUser) {
        Science science = getAnyScienceOrThrow(scienceId);
        checkCanManage(science, currentUser);
        if (science.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu fanni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
        }
        scienceRepository.delete(science);
    }

    private Science getScienceOrThrow(Long scienceId) {
        Science science = getAnyScienceOrThrow(scienceId);
        if (science.getDeletedAt() != null) {
            throw new NoSuchElementException("Fan topilmadi");
        }
        return science;
    }

    // FAQAT "O'chirilganlar savati" amallari (restoreScience,
    // permanentlyDeleteScience, getDeletedSciences) uchun — soft-delete
    // qilingan fanni ham topa oladi.
    private Science getAnyScienceOrThrow(Long scienceId) {
        return scienceRepository.findById(scienceId)
                .orElseThrow(() -> new NoSuchElementException("Fan topilmadi"));
    }

    @Transactional
    public void updateScienceName(Long id, String name, User currentUser) {
        validation.textFieldMustNotBeEmpty(name);
        checkCanManage(getScienceOrThrow(id), currentUser);

        scienceRepository.updateScienceName(id, name);
    }

    // Frontend to'liq tartiblangan id ro'yxatini yuboradi (⬆⬇ yoki A-Z/Z-A
    // saralashdan keyin) — biz orderIndex'larni 1'dan qayta hisoblaymiz
    // (CourseService.reorderSections/TopicSectionService.reorderSections
    // bilan bir xil andoza). ADMIN cheklovi ATAYLAB shu yerda QO'LLANMAYDI —
    // bu BUTUN fanlar ro'yxatini (turli mualliflarga tegishli fanlar
    // aralash) qayta tartiblaydi, bitta fanning egasi emas (foydalanuvchi
    // so'rovi, 2026-09-08'ga ko'ra "o'zgartirish" — kontentga, tartib
    // (order_index) esa umumiy ro'yxat joylashuvi, boshqa fanning
    // kontenti/egaligiga tegmaydi — TopicService.reorderTopics/
    // TopicSectionService.reorderSections/QuestionService.reorderQuestions'dan
    // farqli, ular BITTA fan doirasida bo'lgani uchun tekshiriladi).
    @Transactional
    public void reorderSciences(List<Long> orderedScienceIds) {
        List<Science> sciences = scienceRepository.findAllByDeletedAtIsNullOrderByOrderIndex();
        Map<Long, Science> byId = new LinkedHashMap<>();
        for (Science s : sciences) {
            byId.put(s.getId(), s);
        }

        if (orderedScienceIds.size() != sciences.size() || !byId.keySet().containsAll(orderedScienceIds)) {
            throw new IllegalArgumentException("❌Fanlar ro'yxati mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedScienceIds) {
            byId.get(id).setOrderIndex(index++);
        }
        scienceRepository.saveAll(sciences);
    }

    @Transactional
    public List<ScienceIdAndNameDto> getSciences() {
        return scienceRepository.findAll()
                .stream()
                .map(s -> new ScienceIdAndNameDto(s.getId(), s.getName()))
                .toList();
    }
}