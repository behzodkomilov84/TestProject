package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseChapterRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.QuestionRepository;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final QuestionRepository questionRepository;

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

    // Kursni "O'chirilganlar savati"ga o'tkazish (soft-delete) — DARHOL
    // butunlay o'chirilmaydi. Bo'limlar, mavzular, obunalar, progress
    // yozuvlari HAM tegilmay saqlanadi — "O'chirilganlar" sahifasidan
    // (restoreCourse) bir tugma bilan hammasi bilan birga qaytadan
    // tiklanishi mumkin. Haqiqatan butunlay (qaytarib bo'lmaydigan)
    // o'chirish uchun — permanentlyDeleteCourse (faqat allaqachon
    // "savat"da turgan kursga nisbatan).
    @Transactional
    public void deleteCourse(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        course.setDeletedAt(LocalDateTime.now());
        courseRepository.save(course);
    }

    // "O'chirilganlar savati" ro'yxati — OWNER hammasini, ADMIN faqat
    // o'zi yaratganlarini ko'radi (boshqa CRUD amallar bilan bir xil
    // ruxsat qoidasi).
    @Transactional(readOnly = true)
    public List<CourseDto> getDeletedCourses(User currentUser) {
        List<Course> deleted = currentUser.hasRole("ROLE_OWNER")
                ? courseRepository.findAllDeletedOrderByDeletedAtDesc()
                : courseRepository.findDeletedByCreatedBy_IdOrderByDeletedAtDesc(currentUser.getId());
        return deleted.stream().map(c -> toDto(c, currentUser)).toList();
    }

    // "♻️ Tiklash" — kursni "O'chirilganlar savati"dan qaytaradi, u bilan
    // birga saqlanib qolgan bo'lim/mavzu/obuna/progress ma'lumotlari ham
    // avtomatik yana ko'rinadigan bo'ladi (ular hech qachon o'chirilmagan
    // edi).
    @Transactional
    public void restoreCourse(Long courseId, User currentUser) {
        Course course = getAnyCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        if (course.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu kurs o'chirilmagan — tiklashning hojati yo'q.");
        }

        course.setDeletedAt(null);
        courseRepository.save(course);
    }

    // "🗑️ Butunlay o'chirish" — FAQAT allaqachon "O'chirilganlar savati"da
    // turgan kursga nisbatan (ikki bosqichli himoya — tasodifan bosib
    // yubormaslik uchun). Bu amal QAYTARIB BO'LMAYDI: bo'limlar, obunalar
    // va bo'lim-progress yozuvlari FK RESTRICT bilan bog'langani uchun
    // (courses.sql'da ON DELETE CASCADE yo'q), avval ULARNI, keyin
    // kursning o'zini o'chiramiz.
    @Transactional
    public void permanentlyDeleteCourse(Long courseId, User currentUser) {
        Course course = getAnyCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        if (course.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu kursni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
        }

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

    // Mavzuni (kurs darsi) "O'chirilganlar savati"ga o'tkazish (soft-delete)
    // — DARHOL butunlay o'chirilmaydi, progress yozuvlari HAM tegilmay
    // saqlanadi (Course.deletedAt bilan bir xil g'oya) — "♻️ Tiklash" bilan
    // bir zumda qaytadi.
    @Transactional
    public void deleteSection(Long courseId, Long sectionId, User currentUser) {
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        checkCanManage(section.getCourse(), currentUser);
        section.setDeletedAt(LocalDateTime.now());
        courseSectionRepository.save(section);
    }

    // "O'chirilganlar savati" ro'yxati (kurs ichida).
    @Transactional(readOnly = true)
    public List<CourseSectionTrashDto> getDeletedSections(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        return courseSectionRepository.findDeletedByCourse_Id(courseId);
    }

    // "♻️ Tiklash" — mavzuni savatdan qaytaradi, progress yozuvlari
    // avtomatik yana ko'rinadigan bo'ladi (ular hech qachon o'chirilmagan edi).
    @Transactional
    public void restoreSection(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        CourseSection section = getAnySectionOrThrow(sectionId, courseId);
        if (section.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu mavzu o'chirilmagan — tiklashning hojati yo'q.");
        }
        section.setDeletedAt(null);
        courseSectionRepository.save(section);
    }

    // "🗑️ Butunlay o'chirish" — FAQAT allaqachon savatda turgan mavzuga
    // nisbatan. QAYTARIB BO'LMAYDI: progress yozuvlari FK RESTRICT
    // bo'lgani uchun avval ular, keyin mavzuning o'zi o'chiriladi.
    @Transactional
    public void permanentlyDeleteSection(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        CourseSection section = getAnySectionOrThrow(sectionId, courseId);
        if (section.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu mavzuni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
        }
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
    // avtomatik topiladi/yaratiladi VA mavzuga biriktiriladi — bu YANGI
    // yaratilayotgan mavzu uchun ham, ALLAQACHON mavjud (kurs bilan
    // avvaldan bog'langan) mavzu uchun ham qo'llanadi. Kurs — HAR DOIM
    // "haqiqiy manba": kursda Bo'lim o'zgartirilsa ("1-BOB"->"2-BOB"),
    // TEST BOSHQARUVIdagi mavzuning bo'limi ham shu bilan birga o'zgaradi;
    // kursda mavzu "Bo'limsiz mavzular"ga o'tkazilsa (chapter == null),
    // TEST BOSHQARUVIdagi mavzu ham "Bo'limsiz"ga qaytariladi — ikki
    // tomon HAR DOIM to'liq mos kelishi kerak (foydalanuvchi so'rovi
    // bo'yicha: "kursdagi holatga qarab TEST BOSHQARUVI to'g'rilansin").
    private Topic resolveLinkedTopic(String scienceName, String topicName, CourseChapter chapter) {
        if (scienceName == null || scienceName.isBlank() || topicName == null || topicName.isBlank()) {
            return null;
        }

        String trimmedScience = scienceName.trim();
        String trimmedTopic = topicName.trim();

        Science science = scienceRepository.findByName(trimmedScience)
                .orElseGet(() -> scienceRepository.save(Science.builder().name(trimmedScience).build()));

        TopicSection resolvedSection = chapter != null ? resolveTopicSection(science, chapter.getName()) : null;

        Optional<Topic> existing = topicRepository.findByScience_IdAndName(science.getId(), trimmedTopic);
        if (existing.isPresent()) {
            Topic topic = existing.get();
            topic.setSection(resolvedSection);
            topicRepository.save(topic);
            return topic;
        }

        Topic topic = Topic.builder().name(trimmedTopic).science(science).section(resolvedSection).build();
        return topicRepository.save(topic);
    }

    // "chapter.getName()" bilan bir xil nomli TopicSection shu FANDA mavjud
    // bo'lsa o'shanga, bo'lmasa yangisi (oxiriga qo'shilib) yaratiladi —
    // TopicSectionService'dagi bilan bir xil find-or-create qoidasi
    // (nom katta-kichik harfga sezgir emas).
    //
    // DIQQAT — BOG'LANISH: shu yerda yaratilgan TopicSection bilan uni
    // "tug'dirgan" CourseChapter orasida to'g'ridan-to'g'ri FK yo'q, faqat
    // NOM orqali bog'lanish (bir martalik, shu yerda). Agar keyinchalik
    // CourseChapter nomi o'zgartirilsa (renameChapter()) — o'sha metod
    // shu nom orqali yaratilgan TopicSection'larni ham TOPIB, ularning
    // nomini AVTOMATIK yangilaydi (syncTopicSectionNamesForChapter()) —
    // ikkala tomon (kurs Bo'limi VA test boshqaruvidagi Bo'lim) sinxron
    // qolishi uchun. Shu ikkita metod bir-biriga bog'liq — birini
    // o'zgartirsangiz, ikkinchisini ham ko'rib chiqing.
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
        String trimmedNewName = newName.trim();

        CourseChapter chapter = courseChapterRepository.findById(chapterId)
                .filter(c -> c.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new NoSuchElementException("Bo'lim topilmadi"));

        String oldName = chapter.getName();
        chapter.setName(trimmedNewName);
        courseChapterRepository.save(chapter);

        // MUHIM BOG'LANISH: resolveTopicSection() shu Bo'lim nomi bilan
        // TEST BOSHQARUVI tomonida (Fan ichida) xuddi shu nomli TopicSection
        // avtomatik yaratgan bo'lishi mumkin (kurs mavzusi Fan/Mavzuga
        // bog'langanda). Bu ikkovi orasida to'g'ridan-to'g'ri FK yo'q —
        // bog'lanish faqat NOM orqali (bir tomonlama, yaratilish paytida).
        // Shu sabab, Bo'lim shu yerda qayta nomlansa, TopicSection tomonda
        // ESKI nom bilan "yetim" qolib ketmasligi uchun — pastdagi
        // sinxronlash BIR YO'NALISHDA (kurs -> test boshqaruvi) shu yerda
        // avtomatik amalga oshiriladi. Aksincha (test boshqaruvida Bo'lim
        // nomini o'zgartirish kursga qaytib ta'sir qilishi) — ATAYLAB
        // qilinmagan, chunki bitta TopicSection bir nechta har xil kursning
        // Bo'limlariga mos kelib qolishi mumkin (umumiy, kursga bog'liq
        // bo'lmagan tushuncha).
        syncTopicSectionNamesForChapter(chapterId, oldName, trimmedNewName);
    }

    // renameChapter() tomonidan chaqiriladi — batafsili izoh o'sha yerda.
    private void syncTopicSectionNamesForChapter(Long chapterId, String oldName, String newName) {
        if (oldName.equalsIgnoreCase(newName)) {
            return; // Faqat registr (katta-kichik harf) o'zgargan — TopicSection nomi baribir bir xil hisoblanadi (ignoreCase), tegishga hojat yo'q.
        }

        List<CourseSection> linkedSections = courseSectionRepository.findByChapter_IdAndLinkedTopicIsNotNull(chapterId);

        // Bir nechta kurs mavzusi xuddi shu TopicSection'ga ishora qilishi
        // mumkin (masalan bir nechta mavzu bitta Fan ichida) — har birini
        // FAQAT BIR MARTA qayta nomlash uchun.
        Set<Long> processedSectionIds = new HashSet<>();

        for (CourseSection cs : linkedSections) {
            Topic topic = cs.getLinkedTopic();
            TopicSection section = topic.getSection();
            if (section == null || !processedSectionIds.add(section.getId())) {
                continue;
            }
            // Admin TEST BOSHQARUVI tomonida shu bo'limni qo'lda BOSHQA
            // nomga o'zgartirgan bo'lishi mumkin — bunday holda avtomatik
            // qayta nomlash SHART EMAS (endi u kurs Bo'limi bilan bog'liq
            // emas, o'z holicha boshqariladi).
            if (!section.getName().equalsIgnoreCase(oldName)) {
                continue;
            }

            // TopicSection jadvalida UNIQUE(science_id, name) cheklovi bor —
            // agar shu Fanda YANGI nom bilan bo'lim allaqachon mavjud bo'lsa,
            // nomni o'zgartirish o'rniga mavzu O'SHA mavjud bo'limga qayta
            // biriktiriladi (resolveTopicSection bilan bir xil qoida).
            Optional<TopicSection> existingWithNewName = topicSectionRepository
                    .findByScience_IdAndNameIgnoreCase(section.getScience().getId(), newName);

            if (existingWithNewName.isPresent()) {
                topic.setSection(existingWithNewName.get());
                topicRepository.save(topic);
            } else {
                section.setName(newName);
                topicSectionRepository.save(section);
            }
        }
    }

    // Kurs Bo'limlari bilan TEST BOSHQARUVIdagi Bo'lim (TopicSection)
    // bog'lanishini QO'LDA majburiy sinxronlashtirish — "🔄 Bo'lim-Mavzu
    // bog'lanishini sinxronlash" tugmasi shu orqali ishlaydi. Odatda bu
    // avtomatik sodir bo'ladi (har safar kurs mavzusi saqlanganda —
    // resolveLinkedTopic), lekin vaqt o'tishi bilan ikki tomon orasida
    // farq paydo bo'lishi mumkin (masalan mavzu shu avtomatik
    // sinxronlash qo'shilishidan OLDIN bog'langan bo'lsa, yoki bir nechta
    // Bo'lim bir xil nomli TopicSection'ni "bo'lishib" ishlatgan bo'lsa —
    // birining nomini o'zgartirish ikkinchisiga ham "sirg'alib" ta'sir
    // qilishi mumkin). Shu metod HAR BIR kurs mavzusini joriy Bo'lim
    // nomiga qarab qayta tekshirib, kerak bo'lsa to'g'rilaydi — SHU
    // JUMLADAN "Bo'limsiz mavzular"ga o'tkazilgan mavzular ham (ular
    // TEST BOSHQARUVIDA ham Bo'limsiz qilinadi, resolveLinkedTopic bilan
    // bir xil qoida).
    // Kursning BARCHA Bo'limlari (hozircha bo'sh — hech qanday mavzuga
    // biriktirilmaganlari ham) — courseDetail.js'dagi Bo'lim tanlash
    // select'ini to'liq to'ldirish uchun (shu jumladan bo'sh bo'limlarni
    // o'chirish imkoniyati bilan birga ko'rsatish).
    @Transactional(readOnly = true)
    public List<CourseChapterDto> getChapters(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        return courseChapterRepository.findByCourseIdWithSectionCount(courseId);
    }

    // Faqat BO'SH (hech qanday mavzuga biriktirilmagan) Bo'limni
    // o'chirishga ruxsat beradi — "🗑️" tugmasi (courseDetail.js, Bo'lim
    // tanlash select'i yonida). Foydali mavzular bilan band bo'lgan
    // Bo'limni tasodifan o'chirib, ularni "yetim" qoldirmaslik uchun.
    @Transactional
    public void deleteChapter(Long courseId, Long chapterId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        CourseChapter chapter = courseChapterRepository.findById(chapterId)
                .filter(c -> c.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new NoSuchElementException("Bo'lim topilmadi"));

        if (courseSectionRepository.existsByChapter_Id(chapterId)) {
            throw new IllegalArgumentException(
                    "❌ Bu bo'limda mavzular bor — avval ularni boshqa bo'limga o'tkazing yoki Bo'limsiz qiling.");
        }

        courseChapterRepository.delete(chapter);
    }

    // "🗑️ Bo'lim + mavzularni birga o'chirish" — FAQAT shu yerdan (kurs
    // ichidan) ishlaydi, TEST BOSHQARUVIdan (topicSections.html) EMAS —
    // foydalanuvchi so'rovi bo'yicha ataylab shu yerga joylashtirilgan
    // (chunki bu amal aynan KURS Bo'limi kontekstida ma'noga ega).
    //
    // deleteChapter'dan farqli, bo'sh bo'lishi SHART EMAS: shu Bo'limdagi
    // BARCHA kurs mavzularini (CourseSection) ham o'chiradi. Agar shu
    // Bo'lim TEST BOSHQARUVIdagi biror Bo'limga (TopicSection) bog'langan
    // bo'lsa — O'SHA Bo'limning mavzulari (savollari bilan birga) ham
    // o'chiriladi. HAMMASI SOFT-DELETE (Course.deletedAt bilan bir xil
    // g'oya) — CourseSection/Topic/Question o'zlarining "O'chirilganlar
    // savati"laridan alohida-alohida tiklanishi mumkin (faqat TopicSection
    // — Bo'limning O'ZI — hard-delete, chunki u shunchaki guruhlash
    // birligi, Topic'lar undan uzilib "bo'limsiz" bo'lib qoladi, xolos).
    // Xavfsizlik: agar bog'langan TopicSection'dagi biror mavzu BOSHQA kurs
    // Bo'limiga ham (shu kursning boshqa Bo'limi yoki boshqa kurs) bog'langan
    // bo'lsa — butun amal rad etiladi (o'sha boshqa bog'lanishni "yetim"
    // qoldirmaslik uchun).
    @Transactional
    public void deleteChapterWithLinkedTopics(Long courseId, Long chapterId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        CourseChapter chapter = courseChapterRepository.findById(chapterId)
                .filter(c -> c.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new NoSuchElementException("Bo'lim topilmadi"));

        List<CourseSection> chapterSections = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId).stream()
                .filter(s -> s.getChapter() != null && s.getChapter().getId().equals(chapterId))
                .toList();

        // Shu Bo'limga bog'langan TEST BOSHQARUVI Bo'lim(lar)i — odatda
        // bitta, lekin nazariy jihatdan bir nechta har xil TopicSection'dan
        // mavzular shu bitta kurs Bo'limiga bog'langan bo'lishi mumkin.
        Set<Long> topicSectionIds = chapterSections.stream()
                .map(CourseSection::getLinkedTopic)
                .filter(Objects::nonNull)
                .map(Topic::getSection)
                .filter(Objects::nonNull)
                .map(TopicSection::getId)
                .collect(Collectors.toSet());

        for (Long topicSectionId : topicSectionIds) {
            for (Topic topic : topicRepository.findBySection_IdAndDeletedAtIsNullOrderByOrderIndexAsc(topicSectionId)) {
                courseSectionRepository.findByLinkedTopic_Id(topic.getId()).ifPresent(link -> {
                    if (!link.getChapter().getId().equals(chapterId)) {
                        throw new IllegalArgumentException("❌ \"" + topic.getName() +
                                "\" mavzusi boshqa kurs bo'limiga ham bog'langan — avval o'sha bog'lanishni olib tashlang.");
                    }
                });
            }
        }

        LocalDateTime now = LocalDateTime.now();
        chapterSections.forEach(s -> s.setDeletedAt(now));
        courseSectionRepository.saveAll(chapterSections);
        courseChapterRepository.delete(chapter);

        for (Long topicSectionId : topicSectionIds) {
            List<Topic> topics = topicRepository.findBySection_IdAndDeletedAtIsNullOrderByOrderIndexAsc(topicSectionId);
            for (Topic topic : topics) {
                questionRepository.softDeleteByTopic_Id(topic.getId());
                topic.setDeletedAt(now);
            }
            topicRepository.saveAll(topics);
            topicSectionRepository.deleteById(topicSectionId);
        }
    }

    // "🗑️ Bo'sh bo'limlarni o'chirish" — shu kursda hech qanday mavzuga
    // biriktirilmagan (sectionCount==0) BARCHA Bo'limlarni bir yo'la
    // o'chiradi (deleteChapter'dagi bilan bir xil xavfsizlik qoidasi —
    // faqat bo'sh bo'limlar, band bo'lganlariga tegilmaydi).
    @Transactional
    public int deleteEmptyChapters(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        List<Long> emptyIds = courseChapterRepository.findByCourseIdWithSectionCount(courseId).stream()
                .filter(c -> c.sectionCount() == 0)
                .map(CourseChapterDto::id)
                .toList();

        if (!emptyIds.isEmpty()) {
            courseChapterRepository.deleteAllById(emptyIds);
        }
        return emptyIds.size();
    }

    @Transactional
    public int syncChapterTopicSections(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        List<CourseSection> sections = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId);

        int updated = 0;
        for (CourseSection cs : sections) {
            Topic topic = cs.getLinkedTopic();
            if (topic == null) {
                continue; // Fan/Mavzuga umuman bog'lanmagan — sinxronlanadigan narsa yo'q.
            }

            // chapter == null (kurs mavzusi "Bo'limsiz mavzular"da) bo'lsa —
            // "to'g'ri" holat ham aynan shu: mavzu TEST BOSHQARUVIDA ham
            // Bo'limsiz bo'lishi kerak (null). resolveLinkedTopic bilan
            // bir xil qoida — kurs har doim "haqiqiy manba".
            CourseChapter chapter = cs.getChapter();
            TopicSection correctSection = chapter != null
                    ? resolveTopicSection(topic.getScience(), chapter.getName())
                    : null;
            TopicSection currentSection = topic.getSection();

            boolean mismatch = (correctSection == null) != (currentSection == null)
                    || (correctSection != null && !correctSection.getId().equals(currentSection.getId()));

            if (mismatch) {
                topic.setSection(correctSection);
                topicRepository.save(topic);
                updated++;
            }
        }

        return updated;
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

    // Oddiy (kundalik) foydalanish uchun — soft-delete qilingan (savatdagi)
    // kurs bu yerda ATAYLAB "topilmadi" deb hisoblanadi (404), aks holda
    // uni oddiy ko'rish/tahrirlash/mavzu qo'shish kabi amallar orqali
    // ham "ko'rish" mumkin bo'lib qolardi.
    private Course getCourseOrThrow(Long courseId) {
        Course course = getAnyCourseOrThrow(courseId);
        if (course.getDeletedAt() != null) {
            throw new NoSuchElementException("Kurs topilmadi");
        }
        return course;
    }

    // FAQAT "O'chirilganlar savati" amallari (restoreCourse,
    // permanentlyDeleteCourse, getDeletedCourses) uchun — soft-delete
    // qilingan kursni ham topa oladi.
    private Course getAnyCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));
    }

    private CourseSection getSectionOrThrow(Long sectionId, Long courseId) {
        CourseSection section = getAnySectionOrThrow(sectionId, courseId);
        if (section.getDeletedAt() != null) {
            throw new NoSuchElementException("Mavzu topilmadi");
        }
        return section;
    }

    // FAQAT "O'chirilganlar savati" amallari (restoreSection,
    // permanentlyDeleteSection, getDeletedSections) uchun — soft-delete
    // qilingan mavzuni ham topa oladi.
    private CourseSection getAnySectionOrThrow(Long sectionId, Long courseId) {
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
                .deletedAt(course.getDeletedAt())
                .build();
    }
}
