package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dto.course.CourseFieldDto;
import behzoddev.testproject.dto.course.CourseFieldSaveDto;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final ScienceRepository scienceRepository;

    // HAQIQIY topilgan bug (2026-09-08): Yo'nalish (CourseField) allaqachon
    // "createdBy" maydoniga ega edi, lekin rename/delete HECH QANDAY
    // egalik tekshiruvisiz edi — istalgan ADMIN istalgan boshqa
    // ADMIN'ning Yo'nalishini o'zgartira/o'chira olardi. Endi Science/
    // Course bilan bir xil qoida: OWNER cheklovsiz, ADMIN faqat O'ZI
    // yaratgan Yo'nalishni. Yo'nalishning O'ZI (ro'yxatda ko'rinishi)
    // HAMON hammaga ochiq — faqat tahrirlash/o'chirish cheklanadi
    // (foydalanuvchi so'rovi: "tahrirlash, o'chirishlarni hidden qilib
    // qo'y" — ko'rish emas).
    private boolean canManageField(CourseField field, User user) {
        return user.hasRole("ROLE_OWNER")
                || (field.getCreatedBy() != null && field.getCreatedBy().getId().equals(user.getId()));
    }

    private void checkCanManageField(CourseField field, User user) {
        if (!canManageField(field, user)) {
            throw new AccessDeniedException("⛔ Faqat o'zingiz yaratgan Yo'nalishni tahrirlashingiz yoki o'chirishingiz mumkin.");
        }
    }

    @Transactional(readOnly = true)
    public List<CourseFieldDto> listFields(User currentUser) {
        return courseFieldRepository.findAllByOrderByOrderIndexAsc().stream()
                .map(f -> toDto(f, currentUser))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseFieldDto> listDeletedFields(User currentUser) {
        return courseFieldRepository.findAllDeletedOrderByDeletedAtDesc().stream()
                .map(f -> toDto(f, currentUser))
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
        return toDto(field, creator);
    }

    @Transactional
    public CourseFieldDto renameField(Long fieldId, String name, User currentUser) {
        validateName(name);
        CourseField field = getFieldOrThrow(fieldId);
        checkCanManageField(field, currentUser);
        field.setName(name.trim());
        courseFieldRepository.save(field);
        return toDto(field, currentUser);
    }

    // Faqat BO'SH (hech qanday faol Kursga/Bo'limga biriktirilmagan)
    // Yo'nalishni o'chirish mumkin — aks holda o'sha kurslar/bo'limlar
    // "yetim" (Yo'nalishsiz) bo'lib qolib, foydalanuvchi buni bilmay
    // qolishi mumkin edi (CourseChapterRepository.existsByChapter_Id bilan
    // bir xil himoya g'oyasi). Ikkala tomon HAM tekshiriladi — Course
    // (kurslar katalogi) VA Science (TEST BOSHQARUVI) — chunki Yo'nalish
    // ikkalasi uchun ham UMUMIY (foydalanuvchi so'rovi, 2026-09-05).
    @Transactional
    public void deleteField(Long fieldId, User currentUser) {
        CourseField field = getFieldOrThrow(fieldId);
        checkCanManageField(field, currentUser);

        if (courseFieldRepository.existsActiveCourseByField_Id(fieldId)) {
            throw new IllegalArgumentException(
                    "❌ Bu Yo'nalishda hali kurslar (Bo'limlar) bor — avval ularni boshqa Yo'nalishga o'tkazing yoki o'chiring.");
        }
        if (courseFieldRepository.existsActiveScienceByField_Id(fieldId)) {
            throw new IllegalArgumentException(
                    "❌ Bu Yo'nalishda hali TEST BOSHQARUVI bo'limlari bor — avval ularni boshqa Yo'nalishga o'tkazing yoki o'chiring.");
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
    public void restoreField(Long fieldId, User currentUser) {
        CourseField field = courseFieldRepository.findById(fieldId)
                .orElseThrow(() -> new NoSuchElementException("Yo'nalish topilmadi"));
        checkCanManageField(field, currentUser);

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

    private CourseFieldDto toDto(CourseField field, User currentUser) {
        int courseCount = (int) courseRepository.countByField_IdAndDeletedAtIsNull(field.getId());
        int scienceCount = (int) scienceRepository.countByField_IdAndDeletedAtIsNull(field.getId());
        return CourseFieldDto.builder()
                .id(field.getId())
                .name(field.getName())
                .orderIndex(field.getOrderIndex())
                .courseCount(courseCount)
                .scienceCount(scienceCount)
                .createdAt(field.getCreatedAt())
                .deletedAt(field.getDeletedAt())
                .canManage(canManageField(field, currentUser))
                .build();
    }
}
