package behzoddev.testproject.service;

import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.science.ScienceDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.science.ScienceNameDto;
import behzoddev.testproject.dto.science.ScienceTrashDto;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.ScienceMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScienceService {

    private final ScienceRepository scienceRepository;
    private final TopicRepository topicRepository;
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

    @Transactional
    public Science saveScience(ScienceNameDto scienceNameDto) {

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

        return scienceRepository.save(science);
    }

    @Transactional
    public Science saveScience(Science science) {
        validation.textFieldMustNotBeEmpty(science.getName());

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
    public void removeScience(Long scienceId) {
        Science science = getScienceOrThrow(scienceId);
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
    public void restoreScience(Long scienceId) {
        Science science = getAnyScienceOrThrow(scienceId);
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
    public void permanentlyDeleteScience(Long scienceId) {
        Science science = getAnyScienceOrThrow(scienceId);
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
    public void updateScienceName(Long id, String name) {
        validation.textFieldMustNotBeEmpty(name);

        scienceRepository.updateScienceName(id, name);
    }

    @Transactional
    public List<ScienceIdAndNameDto> getSciences() {
        return scienceRepository.findAll()
                .stream()
                .map(s -> new ScienceIdAndNameDto(s.getId(), s.getName()))
                .toList();
    }
}