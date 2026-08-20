package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dto.course.*;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.CourseSectionProgress;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSectionType;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import behzoddev.testproject.entity.enums.VideoSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * JavaRush uslubidagi online kurslar — OWNER yaratadi/tahrirlaydi, ADMIN/USER
 * obuna orqali kirish huquqini sotib oladi (CourseSubscription, faqat OWNER
 * qo'lda tasdiqlaydi). Bo'limlar ketma-ket ochiladi: 1-bo'lim obunadan
 * keyin darhol, keyingilari — oldingisi "tugatilgach" (CourseSectionProgress).
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseSectionProgressRepository courseSectionProgressRepository;

    /* ================= KATALOG / KO'RISH ================= */

    @Transactional(readOnly = true)
    public List<CourseDto> listCatalog(User currentUser) {
        boolean isOwner = currentUser.hasRole("ROLE_OWNER");

        List<Course> courses = isOwner
                ? courseRepository.findAllByOrderByCreatedAtDesc()
                // ADMIN — chop etilganlar + o'zi yaratgan qoralamalar ham
                // (aks holda hali chop etilmagan o'z kursini topolmasdi).
                : courseRepository.findByPublishedTrueOrCreatedBy_IdOrderByCreatedAtDesc(currentUser.getId());

        return courses.stream().map(c -> toDto(c, currentUser)).toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailDto getDetail(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        // ADMIN o'zi yaratgan kursni OWNER kabi to'liq boshqara olishi kerak —
        // qoralama (unpublished) holatida ham ko'rishi, bo'lim qulflarisiz
        // ko'rishi va tahrirlashi mumkin bo'lishi uchun.
        boolean canManage = canManageCourse(course, currentUser);

        if (!course.isPublished() && !canManage) {
            throw new NoSuchElementException("Kurs topilmadi");
        }

        boolean subscribed = isSubscribed(currentUser, course);
        boolean requestPending = !subscribed && courseSubscriptionRepository
                .existsByUser_IdAndCourse_IdAndStatus(currentUser.getId(), courseId, CourseSubscriptionStatus.PENDING);

        List<CourseSection> sections = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId);

        List<CourseSectionSummaryDto> sectionDtos = sections.stream()
                .map(s -> CourseSectionSummaryDto.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .orderIndex(s.getOrderIndex())
                        .type(s.getType().name())
                        .locked(!canManage && !isSectionUnlocked(currentUser, s, subscribed))
                        .completed(courseSectionProgressRepository
                                .existsByUser_IdAndSection_Id(currentUser.getId(), s.getId()))
                        .build())
                .toList();

        return CourseDetailDto.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .coverImageUrl(course.getCoverImageUrl())
                .published(course.isPublished())
                .subscribed(subscribed || canManage)
                .requestPending(requestPending)
                .canManage(canManage)
                .sections(sectionDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public CourseSectionContentDto getSectionContent(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        boolean subscribed = isSubscribed(currentUser, course);
        boolean canManage = canManageCourse(course, currentUser);

        if (!canManage && !isSectionUnlocked(currentUser, section, subscribed)) {
            throw new AccessDeniedException("⛔ Bu bo'lim hali ochilmagan. Avval oldingi bo'limni tugatish kerak.");
        }

        boolean completed = courseSectionProgressRepository
                .existsByUser_IdAndSection_Id(currentUser.getId(), section.getId());

        CourseSection next = courseSectionRepository
                .findByCourse_IdAndOrderIndex(courseId, section.getOrderIndex() + 1)
                .orElse(null);

        return CourseSectionContentDto.builder()
                .id(section.getId())
                .courseId(course.getId())
                .courseTitle(course.getTitle())
                .title(section.getTitle())
                .orderIndex(section.getOrderIndex())
                .type(section.getType().name())
                .textContent(section.getTextContent())
                .videoSourceType(section.getVideoSourceType() != null ? section.getVideoSourceType().name() : null)
                .videoUrl(section.getVideoUrl())
                .videoDurationSeconds(section.getVideoDurationSeconds())
                .linkedTopicId(section.getLinkedTopic() != null ? section.getLinkedTopic().getId() : null)
                .linkedScienceId(section.getLinkedTopic() != null
                        ? section.getLinkedTopic().getScience().getId() : null)
                .completed(completed)
                .nextSectionId(next != null ? next.getId() : null)
                .nextUnlocked(next != null && (canManage || completed))
                .build();
    }

    // TEXT bo'lim ochilganda, yoki VIDEO bo'lim oxirigacha ko'rilganda chaqiriladi.
    @Transactional
    public void markSectionCompleted(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        boolean canManage = canManageCourse(course, currentUser);
        boolean subscribed = isSubscribed(currentUser, course);

        if (!canManage && !isSectionUnlocked(currentUser, section, subscribed)) {
            throw new AccessDeniedException("⛔ Bu bo'limni tugatish uchun avval ochilgan bo'lishi kerak.");
        }

        if (courseSectionProgressRepository.existsByUser_IdAndSection_Id(currentUser.getId(), section.getId())) {
            return; // Allaqachon belgilangan — idempotent.
        }

        courseSectionProgressRepository.save(CourseSectionProgress.builder()
                .user(currentUser)
                .section(section)
                .build());
    }

    private boolean isSubscribed(User user, Course course) {
        return courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                user.getId(), course.getId(), CourseSubscriptionStatus.CONFIRMED, LocalDateTime.now());
    }

    private boolean isSectionUnlocked(User user, CourseSection section, boolean subscribed) {
        if (!subscribed) return false;
        if (section.getOrderIndex() <= 1) return true;

        return courseSectionRepository
                .findByCourse_IdAndOrderIndex(section.getCourse().getId(), section.getOrderIndex() - 1)
                .map(prev -> courseSectionProgressRepository
                        .existsByUser_IdAndSection_Id(user.getId(), prev.getId()))
                .orElse(true); // oldingi bo'lim topilmasa (data xatosi bo'lmasa kerak) — bloklamaymiz
    }

    /* ================= OWNER: CRUD ================= */

    @Transactional
    public CourseDto createCourse(CourseSaveDto dto, User owner) {
        validateTitle(dto.title());

        Course course = Course.builder()
                .title(dto.title().trim())
                .description(dto.description())
                .coverImageUrl(dto.coverImageUrl())
                .published(dto.published() != null && dto.published())
                .createdBy(owner)
                .build();

        courseRepository.save(course);
        return toDto(course, owner);
    }

    @Transactional
    public CourseDto updateCourse(Long courseId, CourseSaveDto dto, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        validateTitle(dto.title());

        course.setTitle(dto.title().trim());
        course.setDescription(dto.description());
        course.setCoverImageUrl(dto.coverImageUrl());
        if (dto.published() != null) {
            course.setPublished(dto.published());
        }

        courseRepository.save(course);
        return toDto(course, course.getCreatedBy());
    }

    // Kursni o'chirish — bo'limlar, obunalar va bo'lim-progress yozuvlari
    // FK RESTRICT bilan bog'langani uchun (courses.sql'da ON DELETE CASCADE
    // yo'q), avval ULARNI, keyin kursning o'zini o'chiramiz. Aks holda
    // "Cannot delete or update a parent row: a foreign key constraint
    // fails" xatosi chiqib, kurs umuman o'chmasdi (frontend'dagi tasdiqlash
    // xabari "Barcha bo'limlar ham o'chadi" deb va'da bergani kabi).
    @Transactional
    public void deleteCourse(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        courseSectionProgressRepository.deleteBySection_Course_Id(courseId);
        courseSubscriptionRepository.deleteByCourse_Id(courseId);
        courseSectionRepository.deleteByCourse_Id(courseId);
        courseRepository.delete(course);
    }

    // ADMIN faqat O'ZI yaratgan kursni boshqarishi (ko'rish/tahrirlash/
    // o'chirish, qulflardan xoli) mumkin; OWNER — barcha kurslarni
    // (kim yaratganidan qat'i nazar).
    private boolean canManageCourse(Course course, User user) {
        return user.hasRole("ROLE_OWNER")
                || (course.getCreatedBy() != null && course.getCreatedBy().getId().equals(user.getId()));
    }

    private void checkCanManage(Course course, User currentUser) {
        if (!canManageCourse(course, currentUser)) {
            throw new AccessDeniedException("⛔ Faqat o'zingiz yaratgan kursni tahrirlashingiz yoki o'chirishingiz mumkin.");
        }
    }

    @Transactional
    public CourseSectionSummaryDto addSection(Long courseId, CourseSectionSaveDto dto, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        validateSectionDto(dto);

        int nextOrder = courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(courseId)
                .map(s -> s.getOrderIndex() + 1)
                .orElse(1);

        CourseSection section = buildSectionFromDto(dto, course, nextOrder);
        courseSectionRepository.save(section);

        return CourseSectionSummaryDto.builder()
                .id(section.getId())
                .title(section.getTitle())
                .orderIndex(section.getOrderIndex())
                .type(section.getType().name())
                .locked(false)
                .completed(false)
                .build();
    }

    @Transactional
    public void updateSection(Long courseId, Long sectionId, CourseSectionSaveDto dto, User currentUser) {
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        checkCanManage(section.getCourse(), currentUser);
        validateSectionDto(dto);

        section.setTitle(dto.title().trim());
        section.setType(CourseSectionType.valueOf(dto.type()));
        section.setTextContent(dto.textContent());
        section.setVideoSourceType(dto.videoSourceType() != null ? VideoSourceType.valueOf(dto.videoSourceType()) : null);
        section.setVideoUrl(dto.videoUrl());
        section.setVideoDurationSeconds(dto.videoDurationSeconds());

        courseSectionRepository.save(section);
    }

    @Transactional
    public void deleteSection(Long courseId, Long sectionId, User currentUser) {
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        checkCanManage(section.getCourse(), currentUser);
        courseSectionRepository.delete(section);
    }

    private CourseSection buildSectionFromDto(CourseSectionSaveDto dto, Course course, int orderIndex) {
        return CourseSection.builder()
                .course(course)
                .title(dto.title().trim())
                .orderIndex(orderIndex)
                .type(CourseSectionType.valueOf(dto.type()))
                .textContent(dto.textContent())
                .videoSourceType(dto.videoSourceType() != null ? VideoSourceType.valueOf(dto.videoSourceType()) : null)
                .videoUrl(dto.videoUrl())
                .videoDurationSeconds(dto.videoDurationSeconds())
                .build();
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("❌Kurs nomi bo'sh bo'lishi mumkin emas.");
        }
    }

    private void validateSectionDto(CourseSectionSaveDto dto) {
        if (dto.title() == null || dto.title().isBlank()) {
            throw new IllegalArgumentException("❌Bo'lim nomi bo'sh bo'lishi mumkin emas.");
        }

        CourseSectionType type;
        try {
            type = CourseSectionType.valueOf(dto.type());
        } catch (Exception e) {
            throw new IllegalArgumentException("❌Bo'lim turi noto'g'ri (TEXT, VIDEO yoki MIXED bo'lishi kerak).");
        }

        // MIXED — bo'limda ham matn, ham video bo'lgani uchun ikkala tekshiruv
        // ham (TEXT va VIDEO uchun alohida yozilganlar) qo'llaniladi.
        if ((type == CourseSectionType.TEXT || type == CourseSectionType.MIXED)
                && (dto.textContent() == null || dto.textContent().isBlank())) {
            throw new IllegalArgumentException("❌Matn kontenti bo'sh bo'lishi mumkin emas.");
        }

        if (type == CourseSectionType.VIDEO || type == CourseSectionType.MIXED) {
            if (dto.videoSourceType() == null || dto.videoUrl() == null || dto.videoUrl().isBlank()) {
                throw new IllegalArgumentException("❌Video manba va URL ko'rsatilishi shart.");
            }
            try {
                VideoSourceType.valueOf(dto.videoSourceType());
            } catch (Exception e) {
                throw new IllegalArgumentException("❌Video manba turi noto'g'ri.");
            }
        }
    }

    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));
    }

    private CourseSection getSectionOrThrow(Long sectionId, Long courseId) {
        CourseSection section = courseSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Bo'lim topilmadi"));

        if (!section.getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("❌Bo'lim bu kursga tegishli emas.");
        }

        return section;
    }

    private CourseDto toDto(Course course, User currentUser) {
        int sectionCount = (int) courseSectionRepository.countByCourse_Id(course.getId());
        boolean subscribed = currentUser != null && isSubscribed(currentUser, course);

        return CourseDto.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .coverImageUrl(course.getCoverImageUrl())
                .published(course.isPublished())
                .sectionCount(sectionCount)
                .subscribed(subscribed)
                .createdAt(course.getCreatedAt())
                .build();
    }
}
