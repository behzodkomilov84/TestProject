package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseChapterRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.course.*;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseChapter;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.CourseSectionProgress;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSectionContentFormat;
import behzoddev.testproject.entity.enums.CourseSectionType;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import behzoddev.testproject.entity.enums.VideoSourceType;
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
    private final CourseChapterRepository courseChapterRepository;
    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseSectionProgressRepository courseSectionProgressRepository;
    private final ScienceRepository scienceRepository;
    private final TopicRepository topicRepository;
    private final TopicSectionRepository topicSectionRepository;

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
                        .linkedTopicId(s.getLinkedTopic() != null ? s.getLinkedTopic().getId() : null)
                        .linkedScienceId(s.getLinkedTopic() != null ? s.getLinkedTopic().getScience().getId() : null)
                        .chapterId(s.getChapter() != null ? s.getChapter().getId() : null)
                        .chapterName(s.getChapter() != null ? s.getChapter().getName() : null)
                        .chapterOrderIndex(s.getChapter() != null ? s.getChapter().getOrderIndex() : null)
                        .build())
                .toList();

        return CourseDetailDto.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .coverImageUrl(course.getCoverImageUrl())
                .published(course.isPublished())
                .free(course.isFree())
                .price(course.getPrice())
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
            throw new AccessDeniedException("⛔ Bu mavzu hali ochilmagan. Avval oldingi mavzuni tugatish kerak.");
        }

        boolean completed = courseSectionProgressRepository
                .existsByUser_IdAndSection_Id(currentUser.getId(), section.getId());

        CourseSection prev = courseSectionRepository
                .findByCourse_IdAndOrderIndex(courseId, section.getOrderIndex() - 1)
                .orElse(null);
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
                .textContentFormat(section.getTextContentFormat() != null
                        ? section.getTextContentFormat().name() : CourseSectionContentFormat.PLAIN.name())
                .videoSourceType(section.getVideoSourceType() != null ? section.getVideoSourceType().name() : null)
                .videoUrl(section.getVideoUrl())
                .videoDurationSeconds(section.getVideoDurationSeconds())
                .linkedTopicId(section.getLinkedTopic() != null ? section.getLinkedTopic().getId() : null)
                .linkedScienceId(section.getLinkedTopic() != null
                        ? section.getLinkedTopic().getScience().getId() : null)
                .linkedTopicName(section.getLinkedTopic() != null ? section.getLinkedTopic().getName() : null)
                .linkedScienceName(section.getLinkedTopic() != null
                        ? section.getLinkedTopic().getScience().getName() : null)
                .chapterId(section.getChapter() != null ? section.getChapter().getId() : null)
                .chapterName(section.getChapter() != null ? section.getChapter().getName() : null)
                .completed(completed)
                .prevSectionId(prev != null ? prev.getId() : null)
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
            throw new AccessDeniedException("⛔ Bu mavzuni tugatish uchun avval ochilgan bo'lishi kerak.");
        }

        if (courseSectionProgressRepository.existsByUser_IdAndSection_Id(currentUser.getId(), section.getId())) {
            return; // Allaqachon belgilangan — idempotent.
        }

        courseSectionProgressRepository.save(CourseSectionProgress.builder()
                .user(currentUser)
                .section(section)
                .build());
    }

    // "Bepul" (free) kurs — obunasiz ham (site'da HAM, Telegram bot'da HAM)
    // hammaga to'liq ochiq, shuning uchun bunday kursda foydalanuvchi har
    // doim "obunasi bor"dek hisoblanadi (haqiqiy CourseSubscription
    // yozuvisiz ham). Shu bitta joydagi o'zgarish butun ilova bo'ylab
    // (bo'lim qulflari, obuna banneri, katalog belgisi, bot) to'g'ri ishlaydi.
    private boolean isSubscribed(User user, Course course) {
        return course.isFree() || courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
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
                .free(dto.free() != null && dto.free())
                .price(dto.price())
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
        course.setPrice(dto.price());
        if (dto.published() != null) {
            course.setPublished(dto.published());
        }
        if (dto.free() != null) {
            course.setFree(dto.free());
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
        courseChapterRepository.deleteByCourse_Id(courseId);
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
        // Avval Bo'lim (chapter) hal qilinadi — keyin mavzu bog'lanishida
        // (resolveLinkedTopic) shu Bo'lim nomidan TEST BOSHQARUVI tomonida
        // ham mos TopicSection avtomatik yaratish/topish uchun foydalaniladi.
        CourseChapter chapter = resolveChapter(course, dto.chapterId(), dto.newChapterName());
        section.setChapter(chapter);
        section.setLinkedTopic(resolveLinkedTopic(dto.scienceName(), dto.topicName(), chapter));
        courseSectionRepository.save(section);

        return CourseSectionSummaryDto.builder()
                .id(section.getId())
                .title(section.getTitle())
                .orderIndex(section.getOrderIndex())
                .type(section.getType().name())
                .locked(false)
                .completed(false)
                .linkedTopicId(section.getLinkedTopic() != null ? section.getLinkedTopic().getId() : null)
                .linkedScienceId(section.getLinkedTopic() != null
                        ? section.getLinkedTopic().getScience().getId() : null)
                .chapterId(section.getChapter() != null ? section.getChapter().getId() : null)
                .chapterName(section.getChapter() != null ? section.getChapter().getName() : null)
                .chapterOrderIndex(section.getChapter() != null ? section.getChapter().getOrderIndex() : null)
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
        section.setTextContentFormat(parseContentFormat(dto.textContentFormat()));
        section.setVideoSourceType(dto.videoSourceType() != null ? VideoSourceType.valueOf(dto.videoSourceType()) : null);
        section.setVideoUrl(dto.videoUrl());
        section.setVideoDurationSeconds(dto.videoDurationSeconds());
        // Bo'lim tanlansa/o'zgartirilsa — mavzu darhol o'sha bo'limga
        // "o'tib qoladi" (foydalanuvchi so'rovi bo'yicha); bo'sh
        // qoldirilsa — "Bo'limsiz"ga qaytadi (unlink). Avval hal qilinadi —
        // keyin mavzu bog'lanishida (resolveLinkedTopic) shu Bo'lim
        // nomidan TEST BOSHQARUVI tomonida ham mos TopicSection
        // yaratish/topish uchun foydalaniladi.
        CourseChapter chapter = resolveChapter(section.getCourse(), dto.chapterId(), dto.newChapterName());
        section.setChapter(chapter);
        section.setLinkedTopic(resolveLinkedTopic(dto.scienceName(), dto.topicName(), chapter));

        courseSectionRepository.save(section);
    }

    // Bo'limni o'chirishdan oldin unga tegishli progress yozuvlarini ham
    // o'chiramiz — aks holda course_section_progress.section_id FK RESTRICT
    // bo'lgani uchun (biror foydalanuvchi shu bo'limni "tugatilgan" deb
    // belgilagan bo'lsa) "Cannot delete or update a parent row" xatosi bilan
    // muvaffaqiyatsiz tugaydi (deleteCourse'dagi xuddi shu turdagi bug bilan bir xil).
    @Transactional
    public void deleteSection(Long courseId, Long sectionId, User currentUser) {
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        checkCanManage(section.getCourse(), currentUser);
        courseSectionProgressRepository.deleteBySection_Id(sectionId);
        courseSectionRepository.delete(section);
    }

    // Bo'limlar tartibini qayta belgilash — "yuqoriga/pastga" ko'chirish va
    // A→Z/Z→A saralash bir xil endpoint orqali ishlaydi: frontend yangi
    // tartibdagi ID ro'yxatini yuboradi, biz orderIndex'larni 1'dan qayta
    // hisoblaymiz.
    @Transactional
    public void reorderSections(Long courseId, List<Long> orderedSectionIds, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        List<CourseSection> sections = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId);
        Map<Long, CourseSection> byId = new LinkedHashMap<>();
        for (CourseSection s : sections) {
            byId.put(s.getId(), s);
        }

        if (orderedSectionIds.size() != sections.size() || !byId.keySet().containsAll(orderedSectionIds)) {
            throw new IllegalArgumentException("❌Mavzular ro'yxati kursning mavzulariga mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedSectionIds) {
            byId.get(id).setOrderIndex(index++);
        }
        courseSectionRepository.saveAll(sections);
    }

    // ADMIN/OWNER bo'lim qo'shayotganda/tahrirlayotganda fan+mavzu nomini
    // kiritsa — TEST BOSHQARUVI'da ular hali mavjud bo'lmasa avtomatik
    // yaratiladi (alohida TEST BOSHQARUVI sahifasiga o'tib, mos ID qidirib
    // yurishga hojat qolmaydi). Ikkalasi ham bo'sh bo'lsa — bog'lanish
    // olib tashlanadi (unlink).
    //
    // "chapter" — shu kurs mavzusi qaysi Bo'limga (CourseChapter) tegishli
    // bo'lsa, TEST BOSHQARUVI tomonida ham xuddi shu nomli Bo'lim (TopicSection)
    // avtomatik topiladi/yaratiladi va YANGI yaratilayotgan mavzuga
    // biriktiriladi — ikki tomondagi "Bo'lim" tuzilmasi mos kelishi uchun
    // (foydalanuvchi so'rovi bo'yicha). MUHIM: faqat mavzu YANGI
    // yaratilayotganda qo'llanadi — allaqachon mavjud (ehtimol admin
    // tomonidan test-boshqaruvida qo'lda tashkil qilingan) mavzuning
    // bo'limi bu yerdan hech qachon o'zgartirilmaydi/bosib yozilmaydi.
    private Topic resolveLinkedTopic(String scienceName, String topicName, CourseChapter chapter) {
        if (scienceName == null || scienceName.isBlank() || topicName == null || topicName.isBlank()) {
            return null;
        }

        String trimmedScience = scienceName.trim();
        String trimmedTopic = topicName.trim();

        Science science = scienceRepository.findByName(trimmedScience)
                .orElseGet(() -> scienceRepository.save(Science.builder().name(trimmedScience).build()));

        Optional<Topic> existing = topicRepository.findByScience_IdAndName(science.getId(), trimmedTopic);
        if (existing.isPresent()) {
            return existing.get();
        }

        Topic topic = Topic.builder().name(trimmedTopic).science(science).build();
        if (chapter != null) {
            topic.setSection(resolveTopicSection(science, chapter.getName()));
        }
        return topicRepository.save(topic);
    }

    // "chapter.getName()" bilan bir xil nomli TopicSection shu FANDA mavjud
    // bo'lsa o'shanga, bo'lmasa yangisi (oxiriga qo'shilib) yaratiladi —
    // TopicSectionService'dagi bilan bir xil find-or-create qoidasi
    // (nom katta-kichik harfga sezgir emas).
    private TopicSection resolveTopicSection(Science science, String sectionName) {
        return topicSectionRepository.findByScience_IdAndNameIgnoreCase(science.getId(), sectionName)
                .orElseGet(() -> {
                    Integer maxOrder = topicSectionRepository.findMaxOrderIndexByScienceId(science.getId());
                    return topicSectionRepository.save(TopicSection.builder()
                            .science(science)
                            .name(sectionName)
                            .orderIndex(maxOrder != null ? maxOrder + 1 : 1)
                            .build());
                });
    }

    // "chapterId" berilgan bo'lsa — ANIQ shu (mavjud) bo'lim ishlatiladi
    // (frontend'da endi erkin matn emas, tanlov/select — shuning uchun
    // yozuvdagi kichik farq tufayli tasodifan yangi dublikat bo'lim
    // yaratilib ketish muammosi endi yo'q). Aks holda "newChapterName"
    // berilsa (foydalanuvchi "➕ Yangi bo'lim" variantini tanlagan) — shu
    // KURSDA shu nomli bo'lim mavjud bo'lmasa avtomatik yaratiladi (oxiriga
    // qo'shiladi), mavjud bo'lsa (nom katta-kichik harfga sezgir emas)
    // o'shanga biriktiriladi — bu ikkinchi tekshiruv shunchaki xavfsizlik
    // to'ri, asosiy yo'l endi "chapterId" orqali. Ikkalasi ham bo'lmasa —
    // "Bo'limsiz" (unlink).
    private CourseChapter resolveChapter(Course course, Long chapterId, String newChapterName) {
        if (chapterId != null) {
            return courseChapterRepository.findById(chapterId)
                    .filter(c -> c.getCourse().getId().equals(course.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("❌ Tanlangan bo'lim topilmadi."));
        }

        if (newChapterName == null || newChapterName.isBlank()) {
            return null;
        }

        String trimmed = newChapterName.trim();

        return courseChapterRepository.findByCourse_IdAndNameIgnoreCase(course.getId(), trimmed)
                .orElseGet(() -> {
                    int nextOrder = courseChapterRepository.findTopByCourse_IdOrderByOrderIndexDesc(course.getId())
                            .map(c -> c.getOrderIndex() + 1)
                            .orElse(1);
                    return courseChapterRepository.save(CourseChapter.builder()
                            .course(course)
                            .name(trimmed)
                            .orderIndex(nextOrder)
                            .build());
                });
    }

    // Bo'lim nomini o'zgartirish — CourseChapter BITTA umumiy yozuv bo'lgani
    // (har bir mavzuda alohida saqlangan matn emas) uchun, shu yerda bir
    // marta o'zgartirilishi bilan unga biriktirilgan BARCHA mavzularda ham
    // (chapter_id FK orqali) darhol avtomatik yangilanadi — mavzularni
    // birma-bir tahrirlab chiqish shart emas.
    @Transactional
    public void renameChapter(Long courseId, Long chapterId, String newName, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("❌ Bo'lim nomi bo'sh bo'lishi mumkin emas.");
        }

        CourseChapter chapter = courseChapterRepository.findById(chapterId)
                .filter(c -> c.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new NoSuchElementException("Bo'lim topilmadi"));

        chapter.setName(newName.trim());
        courseChapterRepository.save(chapter);
    }

    private CourseSection buildSectionFromDto(CourseSectionSaveDto dto, Course course, int orderIndex) {
        return CourseSection.builder()
                .course(course)
                .title(dto.title().trim())
                .orderIndex(orderIndex)
                .type(CourseSectionType.valueOf(dto.type()))
                .textContent(dto.textContent())
                .textContentFormat(parseContentFormat(dto.textContentFormat()))
                .videoSourceType(dto.videoSourceType() != null ? VideoSourceType.valueOf(dto.videoSourceType()) : null)
                .videoUrl(dto.videoUrl())
                .videoDurationSeconds(dto.videoDurationSeconds())
                .build();
    }

    // Frontend "PLAIN" (qo'lda yozilgan) yoki "HTML" (.docx'dan mammoth.js
    // orqali import qilingan, formatlash saqlangan) qiymatini yuboradi;
    // bo'sh/noto'g'ri qiymat — orqaga moslik uchun PLAIN deb qabul qilinadi.
    private CourseSectionContentFormat parseContentFormat(String format) {
        if (format == null || format.isBlank()) {
            return CourseSectionContentFormat.PLAIN;
        }
        try {
            return CourseSectionContentFormat.valueOf(format);
        } catch (IllegalArgumentException e) {
            return CourseSectionContentFormat.PLAIN;
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("❌Kurs nomi bo'sh bo'lishi mumkin emas.");
        }
    }

    // Haqiqiy production bug: nom 200 belgidan (eski ustun uzunligi) oshib
    // ketsa, DB "Data too long for column 'title'" xatosi berardi va
    // GlobalRestExceptionHandler buni FK xatolariga mo'ljallangan umumiy
    // "bog'liq ma'lumotlar mavjud" xabari bilan chalg'ituvchi tarzda
    // qaytarardi. Ustun 500 belgigacha kengaytirildi, lekin baribir aniq
    // chegara va tushunarli xabar bilan oldindan tekshiramiz.
    private static final int SECTION_TITLE_MAX_LENGTH = 500;

    private void validateSectionDto(CourseSectionSaveDto dto) {
        if (dto.title() == null || dto.title().isBlank()) {
            throw new IllegalArgumentException("❌Mavzu nomi bo'sh bo'lishi mumkin emas.");
        }

        if (dto.title().trim().length() > SECTION_TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("❌Mavzu nomi juda uzun (ko'pi bilan "
                    + SECTION_TITLE_MAX_LENGTH + " ta belgi bo'lishi kerak).");
        }

        CourseSectionType type;
        try {
            type = CourseSectionType.valueOf(dto.type());
        } catch (Exception e) {
            throw new IllegalArgumentException("❌Mavzu turi noto'g'ri (TEXT, VIDEO yoki MIXED bo'lishi kerak).");
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
                .orElseThrow(() -> new NoSuchElementException("Mavzu topilmadi"));

        if (!section.getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("❌Mavzu bu kursga tegishli emas.");
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
                .free(course.isFree())
                .price(course.getPrice())
                .sectionCount(sectionCount)
                .subscribed(subscribed)
                .createdAt(course.getCreatedAt())
                .build();
    }
}
