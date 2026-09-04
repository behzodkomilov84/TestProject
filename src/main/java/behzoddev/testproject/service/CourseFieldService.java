package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dto.course.CourseFieldDto;
import behzoddev.testproject.dto.course.CourseFieldSaveDto;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// "Yo'nalish" (soha) — Kurslar (Bo'limlar) katalogini kattaroq guruhga
// bo'ladi (masalan "Sanitariya epidemiologiya xizmati", "O'rta ta'lim").
// CRUD — OWNER va ADMIN ikkalasi ham (kurs yaratish huquqi bilan bir xil,
// foydalanuvchi so'rovi, 2026-09-04).
@Service
@RequiredArgsConstructor
public class CourseFieldService {

    private final CourseFieldRepository courseFieldRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<CourseFieldDto> listFields() {
        return courseFieldRepository.findAllByOrderByOrderIndexAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseFieldDto> listDeletedFields() {
        return courseFieldRepository.findAllDeletedOrderByDeletedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CourseFieldDto createField(CourseFieldSaveDto dto, User creator) {
        validateName(dto.name());

        int nextOrderIndex = courseFieldRepository.findTopByOrderIndexDesc()
                .map(f -> f.getOrderIndex() + 1)
                .orElse(1);

        CourseField field = CourseField.builder()
                .name(dto.name().trim())
                .orderIndex(nextOrderIndex)
                .createdBy(creator)
                .build();

        courseFieldRepository.save(field);
        return toDto(field);
    }

    @Transactional
    public CourseFieldDto renameField(Long fieldId, String name) {
        validateName(name);
        CourseField field = getFieldOrThrow(fieldId);
        field.setName(name.trim());
        courseFieldRepository.save(field);
        return toDto(field);
    }

    // Faqat BO'SH (hech qanday faol Bo'limga/Kursga biriktirilmagan)
    // Yo'nalishni o'chirish mumkin — aks holda o'sha kurslar "yetim"
    // (Yo'nalishsiz) bo'lib qolib, foydalanuvchi buni bilmay qolishi
    // mumkin edi (CourseChapterRepository.existsByChapter_Id bilan bir xil
    // himoya g'oyasi).
    @Transactional
    public void deleteField(Long fieldId) {
        CourseField field = getFieldOrThrow(fieldId);

        if (courseFieldRepository.existsActiveCourseByField_Id(fieldId)) {
            throw new IllegalArgumentException(
                    "❌ Bu Yo'nalishda hali kurslar (Bo'limlar) bor — avval ularni boshqa Yo'nalishga o'tkazing yoki o'chiring.");
        }

        field.setDeletedAt(LocalDateTime.now());
        courseFieldRepository.save(field);
    }

    // "⬆⬇" — Yo'nalish kartalarini katalog sahifasida yuqoriga/pastga
    // surish (coursesCatalog.js) — CourseService.reorderChapters bilan
    // bir xil andoza: TO'LIQ (ro'yxatdagi barcha) ID ro'yxati kutiladi.
    @Transactional
    public void reorderFields(List<Long> orderedFieldIds) {
        List<CourseField> fields = courseFieldRepository.findAllByOrderByOrderIndexAsc();
        Map<Long, CourseField> byId = new LinkedHashMap<>();
        for (CourseField f : fields) {
            byId.put(f.getId(), f);
        }

        if (orderedFieldIds.size() != fields.size() || !byId.keySet().containsAll(orderedFieldIds)) {
            throw new IllegalArgumentException("❌ Yo'nalishlar ro'yxati mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedFieldIds) {
            byId.get(id).setOrderIndex(index++);
        }
        courseFieldRepository.saveAll(fields);
    }

    @Transactional
    public void restoreField(Long fieldId) {
        CourseField field = courseFieldRepository.findById(fieldId)
                .orElseThrow(() -> new NoSuchElementException("Yo'nalish topilmadi"));

        if (field.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu Yo'nalish o'chirilmagan — tiklashning hojati yo'q.");
        }

        field.setDeletedAt(null);
        courseFieldRepository.save(field);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("❌ Yo'nalish nomi bo'sh bo'lishi mumkin emas.");
        }
    }

    private CourseField getFieldOrThrow(Long fieldId) {
        return courseFieldRepository.findById(fieldId)
                .filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Yo'nalish topilmadi"));
    }

    private CourseFieldDto toDto(CourseField field) {
        int courseCount = (int) courseRepository.countByField_IdAndDeletedAtIsNull(field.getId());
        return CourseFieldDto.builder()
                .id(field.getId())
                .name(field.getName())
                .orderIndex(field.getOrderIndex())
                .courseCount(courseCount)
                .createdAt(field.getCreatedAt())
                .deletedAt(field.getDeletedAt())
                .build();
    }
}
