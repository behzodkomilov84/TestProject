package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseChapterRepository;
import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.course.*;
import behzoddev.testproject.dto.question.TopicQuestionCountDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseChapter;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.CourseSectionProgress;
import behzoddev.testproject.entity.Question;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JavaRush uslubidagi online kurslar — OWNER yaratadi/tahrirlaydi, ADMIN/USER
 * obuna orqali kirish huquqini sotib oladi (CourseSubscription, faqat OWNER
 * qo'lda tasdiqlaydi). Darslar (CourseSection) o'z MAVZUSI (CourceChapter)
 * ICHIDA ketma-ket ochiladi: har bir Mavzuning 1-darsi obunadan keyin
 * darhol ochiq, keyingilari — o'sha MAVZU ICHIDAGI oldingisi "tugatilgach"
 * (CourseSectionProgress). Mavzular bir-biridan MUSTAQIL — bitta Mavzuni
 * oxirigacha tugatmaslik boshqa Mavzularning 1-darsini bloklamaydi
 * (foydalanuvchi so'rovi bo'yicha, 2026-09-03; "Mavzusiz" — chapter=null —
 * darslar ham o'zaro bitta mustaqil guruh hisoblanadi).
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseFieldRepository courseFieldRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseSectionProgressRepository courseSectionProgressRepository;
    private final ScienceRepository scienceRepository;
    private final TopicRepository topicRepository;
    private final TopicSectionRepository topicSectionRepository;
    private final QuestionRepository questionRepository;

    // "🔗 Darsga havola qo'shish" (topicLinkButton.js#buildTopicLinkHtml)
    // tomonidan izohga qo'shilgan havolani topish uchun — "/courses/
    // {courseId}/sections/{sectionId}" ko'rinishidagi href'ni qidiradi
    // (boshqa (masalan tashqi) havolalarga tegmaydi). "(?:https?://[^/\"]+)?"
    // — ixtiyoriy domen prefiksi: ba'zi ESKI havolalar to'liq URL
    // ("https://study-grow.uz/courses/...") shaklida saqlangan edi —
    // avval bu shakl UMUMAN mos kelmasdi, shu sabab audit "havola yo'q"
    // deb noto'g'ri hisoblab, ustiga YANA bitta (ikkinchi) havola qo'shib
    // qo'yardi (haqiqiy topilgan bug — "2 ta qo'shilib qolyapti").
    private static final Pattern TOPIC_LINK_HREF_PATTERN =
            Pattern.compile("href=\"(?:https?://[^/\"]+)?/courses/(\\d+)/sections/(\\d+)\"");

    // Izohdagi eski/noto'g'ri dars havolasi belgisini (badge) olib
    // tashlash uchun. MUHIM (haqiqiy topilgan bug, 2026-08-31): avval
    // "<span[^>]*>\s*<a" edi — \s* FAQAT bo'sh joyni tutadi, lekin
    // haqiqiy belgida <span> bilan <a> orasida "📖 " (emoji + bo'sh joy)
    // bor — emoji \s* ga mos kelmagani uchun optional span-guruh HECH
    // QACHON ishlamas edi, natijada replaceAll() faqat <a>...</a></span>
    // qismini olib tashlab, "<span ...>📖 " qismini "yetim" holda
    // qoldirib ketardi (buzilgan HTML — bir nechta ochilgan <span> lekin
    // yopilmagan holda qolib ketgan). Endi "<span>" bilan "<a>" orasida
    // (va "</a>" bilan "</span>" orasida) IXTIYORIY istalgan matn
    // (emoji ham) bo'lishi mumkinligi hisobga olingan — shu bilan birga
    // "background:#e8f5f3" (shu belgiga XOS uslub) orqali ANIQ faqat
    // O'ZIMIZ yaratgan belgigagina mos kelishi ta'minlangan (izohdagi
    // boshqa, aloqasiz <span>larga tegmasligi uchun).
    private static final Pattern TOPIC_LINK_BADGE_PATTERN = Pattern.compile(
            "<span[^>]*background:#e8f5f3[^>]*>.*?<a\\s+href=\"(?:https?://[^/\"]+)?/courses/\\d+/sections/\\d+\"[^>]*>.*?</a>.*?</span>" +
                    "|<a\\s+href=\"(?:https?://[^/\"]+)?/courses/\\d+/sections/\\d+\"[^>]*>.*?</a>",
            Pattern.DOTALL
    );

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
        // qoralama (unpublished) holatida ham ko'rishi, dars qulflarisiz
        // ko'rishi va tahrirlashi mumkin bo'lishi uchun.
        boolean canManage = canManageCourse(course, currentUser);

        if (!course.isPublished() && !canManage) {
            throw new NoSuchElementException("Kurs topilmadi");
        }

        boolean subscribed = isSubscribed(currentUser, course);
        boolean requestPending = !subscribed && courseSubscriptionRepository
                .existsByUser_IdAndCourse_IdAndStatus(currentUser.getId(), courseId, CourseSubscriptionStatus.PENDING);

        List<CourseSection> sections = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId);

        // Har bir bog'langan darsning nechta faol savoli borligi — BULK
        // (bitta so'rov, N+1 EMAS) hisoblanadi, kartochkada "N ta test"
        // belgisi uchun (foydalanuvchi so'rovi bo'yicha).
        List<Long> linkedTopicIds = sections.stream()
                .map(CourseSection::getLinkedTopic)
                .filter(Objects::nonNull)
                .map(Topic::getId)
                .distinct()
                .toList();
        Map<Long, Long> questionCountByTopicId = linkedTopicIds.isEmpty()
                ? Map.of()
                : questionRepository.countByTopicIdsGrouped(linkedTopicIds).stream()
                        .collect(Collectors.toMap(TopicQuestionCountDto::topicId, TopicQuestionCountDto::count));

        // Har bir dars uchun, O'ZINING MAVZUSI (chapter) ICHIDAGI undan
        // oldingi eng yaqin darsni bitta marta (N+1 so'rovsiz) hisoblab
        // qo'yamiz — Mavzular bir-biridan mustaqil ochilishi shu orqali
        // ta'minlanadi (computePreviousInChapterMap izohiga qarang).
        Map<Long, CourseSection> previousInChapterBySectionId = computePreviousInChapterMap(sections);

        List<CourseSectionSummaryDto> sectionDtos = sections.stream()
                .map(s -> CourseSectionSummaryDto.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .orderIndex(s.getOrderIndex())
                        .type(s.getType().name())
                        .locked(!canManage && !isSectionUnlockedGivenPrev(
                                currentUser, previousInChapterBySectionId.get(s.getId()), subscribed))
                        .completed(courseSectionProgressRepository
                                .existsByUser_IdAndSection_Id(currentUser.getId(), s.getId()))
                        .linkedTopicId(s.getLinkedTopic() != null ? s.getLinkedTopic().getId() : null)
                        .linkedScienceId(s.getLinkedTopic() != null ? s.getLinkedTopic().getScience().getId() : null)
                        .linkedTopicQuestionCount(s.getLinkedTopic() != null
                                ? questionCountByTopicId.getOrDefault(s.getLinkedTopic().getId(), 0L).intValue()
                                : null)
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
                .fieldId(course.getField() != null ? course.getField().getId() : null)
                .fieldName(course.getField() != null ? course.getField().getName() : null)
                .sections(sectionDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public CourseSectionContentDto getSectionContent(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        boolean subscribed = isSubscribed(currentUser, course);
        boolean canManage = canManageCourse(course, currentUser);

        // Bitta so'rov bilan olingan ro'yxatdan hisoblangan xarita — SHU
        // darsning o'ziga kirish huquqini TEKSHIRISH uchun HAM, pastdagi
        // "Keyingi dars" tugmasi holatini (nextUnlocked) TO'G'RI (Mavzu
        // chegarasidan mustaqil) hisoblash uchun HAM qayta ishlatiladi.
        List<CourseSection> ordered = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId);
        Map<Long, CourseSection> previousInChapterBySectionId = computePreviousInChapterMap(ordered);

        if (!canManage && !isSectionUnlockedGivenPrev(
                currentUser, previousInChapterBySectionId.get(section.getId()), subscribed)) {
            throw new AccessDeniedException("⛔ Bu dars hali ochilmagan. Avval oldingi darsni tugatish kerak.");
        }

        boolean completed = courseSectionProgressRepository
                .existsByUser_IdAndSection_Id(currentUser.getId(), section.getId());

        CourseSection prev = courseSectionRepository
                .findByCourse_IdAndOrderIndex(courseId, section.getOrderIndex() - 1)
                .orElse(null);
        CourseSection next = courseSectionRepository
                .findByCourse_IdAndOrderIndex(courseId, section.getOrderIndex() + 1)
                .orElse(null);

        // "Keyingi dars" tugmasi — agar keyingisi BOSHQA (mustaqil)
        // Mavzuning 1-darsi bo'lsa, u SHU dars tugatilishini kutmasdan
        // ham allaqachon ochiq bo'lishi mumkin (masalan sahifa birinchi
        // marta ochilganda, video hali ko'rilmagan holatda ham).
        boolean nextUnlocked = next != null && (canManage || isSectionUnlockedGivenPrev(
                currentUser, previousInChapterBySectionId.get(next.getId()), subscribed));

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
                .nextUnlocked(nextUnlocked)
                .build();
    }

    // TEXT dars ochilganda, yoki VIDEO dars oxirigacha ko'rilganda chaqiriladi.
    @Transactional
    public void markSectionCompleted(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        CourseSection section = getSectionOrThrow(sectionId, courseId);
        boolean canManage = canManageCourse(course, currentUser);
        boolean subscribed = isSubscribed(currentUser, course);

        if (!canManage && !isSectionUnlocked(currentUser, section, subscribed)) {
            throw new AccessDeniedException("⛔ Bu darsni tugatish uchun avval ochilgan bo'lishi kerak.");
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
    // (dars qulflari, obuna banneri, katalog belgisi, bot) to'g'ri ishlaydi.
    private boolean isSubscribed(User user, Course course) {
        return course.isFree() || courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                user.getId(), course.getId(), CourseSubscriptionStatus.CONFIRMED, LocalDateTime.now());
    }

    // Bitta dars uchun (getSectionContent/markSectionCompleted — loop
    // ICHIDA emas, bitta so'rov uchun bemalol) — kursning BUTUN tartiblangan
    // ro'yxatini olib, shu darsning o'z MAVZUSI ICHIDAGI oldingisini topadi.
    // Ko'p darsni BIRGALIKDA hisoblash kerak bo'lganda (getDetail) buning
    // o'rniga computePreviousInChapterMap() + isSectionUnlockedGivenPrev()
    // ishlatiladi (N+1 so'rovning oldini olish uchun).
    private boolean isSectionUnlocked(User user, CourseSection section, boolean subscribed) {
        List<CourseSection> ordered = courseSectionRepository
                .findByCourse_IdOrderByOrderIndexAsc(section.getCourse().getId());
        CourseSection prevInChapter = computePreviousInChapterMap(ordered).get(section.getId());
        return isSectionUnlockedGivenPrev(user, prevInChapter, subscribed);
    }

    private boolean isSectionUnlockedGivenPrev(User user, CourseSection prevInChapter, boolean subscribed) {
        if (!subscribed) return false;
        if (prevInChapter == null) return true; // shu Mavzu (yoki "mavzusizlar" guruhi) ICHIDAGI birinchi dars

        return courseSectionProgressRepository.existsByUser_IdAndSection_Id(user.getId(), prevInChapter.getId());
    }

    // Kurs ichidagi (orderIndex bo'yicha saralangan) darslar ro'yxatida,
    // har bir darsni O'ZINING MAVZUSI (chapter) bilan guruhlab, shu
    // MAVZU ICHIDAGI eng yaqin OLDINGI darsni topib beradi (birinchisi
    // uchun xarita'da yozuv umuman bo'lmaydi — "oldingisi yo'q" degani).
    // Mavzular bir-biridan MUSTAQIL: masalan II Mavzuning 3-darsi
    // uchun oldingi — II Mavzuning 2-darsi, I Mavzuning oxirgi darsi
    // EMAS (garchi orderIndex bo'yicha undan oldin kelsa ham). "Mavzusiz"
    // (chapter=null) darslar ham o'zaro bitta mustaqil guruh hisoblanadi.
    private Map<Long, CourseSection> computePreviousInChapterMap(List<CourseSection> orderedSections) {
        Map<Long, CourseSection> lastSeenByChapterId = new LinkedHashMap<>();
        Map<Long, CourseSection> previousBySectionId = new LinkedHashMap<>();

        for (CourseSection s : orderedSections) {
            Long chapterKey = s.getChapter() != null ? s.getChapter().getId() : 0L;
            CourseSection prev = lastSeenByChapterId.get(chapterKey);
            if (prev != null) {
                previousBySectionId.put(s.getId(), prev);
            }
            lastSeenByChapterId.put(chapterKey, s);
        }

        return previousBySectionId;
    }

    /* ================= OWNER: CRUD ================= */

    @Transactional
    public CourseDto createCourse(CourseSaveDto dto, User owner) {
        validateTitle(dto.title());
        CourseField field = getFieldOrThrowForAssignment(dto.fieldId());

        // Shu Yo'nalish ICHIDA navbatdagi order_index — CourseFieldService
        // #createField bilan bir xil andoza (⬆⬇ tartiblash uchun).
        int nextOrderIndex = courseRepository.findTopByField_IdAndDeletedAtIsNullOrderByOrderIndexDesc(field.getId())
                .map(c -> (c.getOrderIndex() != null ? c.getOrderIndex() : 0) + 1)
                .orElse(1);

        Course course = Course.builder()
                .title(dto.title().trim())
                .description(dto.description())
                .coverImageUrl(dto.coverImageUrl())
                .published(dto.published() != null && dto.published())
                .free(dto.free() != null && dto.free())
                .price(dto.price())
                .createdBy(owner)
                .field(field)
                .orderIndex(nextOrderIndex)
                .build();

        courseRepository.save(course);
        return toDto(course, owner);
    }

    // "⬆⬇" — kurs kartochkalarini bitta Yo'nalish ICHIDA yuqoriga/pastga
    // surish (coursesCatalog.js#moveCourse) — CourseFieldService
    // #reorderFields bilan bir xil andoza: TO'LIQ (shu Yo'nalishdagi
    // barcha) ID ro'yxati kutiladi. "Yo'nalishsiz kurslar" psevdo-guruhi
    // uchun ishlatilmaydi (bunday kursga yangisi qo'shilmaydi — Yo'nalish
    // yaratishda MAJBURIY).
    @Transactional
    public void reorderCourses(Long fieldId, List<Long> orderedCourseIds) {
        List<Course> courses = courseRepository.findByField_IdAndDeletedAtIsNull(fieldId);
        Map<Long, Course> byId = new LinkedHashMap<>();
        for (Course c : courses) {
            byId.put(c.getId(), c);
        }

        if (orderedCourseIds.size() != courses.size() || !byId.keySet().containsAll(orderedCourseIds)) {
            throw new IllegalArgumentException("❌ Kurslar ro'yxati mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedCourseIds) {
            byId.get(id).setOrderIndex(index++);
        }
        courseRepository.saveAll(courses);
    }

    @Transactional
    public CourseDto updateCourse(Long courseId, CourseSaveDto dto, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        validateTitle(dto.title());
        CourseField field = getFieldOrThrowForAssignment(dto.fieldId());

        course.setTitle(dto.title().trim());
        course.setDescription(dto.description());
        course.setCoverImageUrl(dto.coverImageUrl());
        course.setPrice(dto.price());
        course.setField(field);
        if (dto.published() != null) {
            course.setPublished(dto.published());
        }
        if (dto.free() != null) {
            course.setFree(dto.free());
        }

        courseRepository.save(course);
        return toDto(course, course.getCreatedBy());
    }

    // Yangi kurs yaratishda ("Bo'lim") Yo'nalish MAJBURIY — foydalanuvchi
    // so'rovi bo'yicha (2026-09-04), turli sohalarga tegishli kurslar bitta
    // tekis ro'yxatda aralashib ketmasligi uchun. Tahrirlashda ham har doim
    // yuboriladi (aks holda mavjud bog'lanish tasodifan yo'qolib qolmasin).
    private CourseField getFieldOrThrowForAssignment(Long fieldId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("❌ Yo'nalish tanlanishi shart.");
        }
        return courseFieldRepository.findById(fieldId)
                .filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Yo'nalish topilmadi"));
    }

    // Kursni "O'chirilganlar savati"ga o'tkazish (soft-delete) — DARHOL
    // butunlay o'chirilmaydi. Mavzular, darslar, obunalar, progress
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
    // birga saqlanib qolgan mavzu/dars/obuna/progress ma'lumotlari ham
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
    // yubormaslik uchun).
    //
    // ROLE_OWNER uchun — bu amal QAYTARIB BO'LMAYDI: mavzular, darslar, obunalar
    // va dars-progress yozuvlari FK RESTRICT bilan bog'langani uchun
    // (courses.sql'da ON DELETE CASCADE yo'q), avval ULARNI, keyin
    // kursning o'zini o'chiramiz.
    //
    // ROLE_ADMIN uchun — foydalanuvchi ANIQ talabi bo'yicha HAQIQIY
    // o'chirilmaydi: kurs shu ADMIN'dan (getDeletedCourses) va katalogdan
    // yo'qoladi, lekin ma'lumotlari BUTUNLAY saqlanadi — faqat "arxivlangan"
    // deb belgilanadi (archiveCourseAsAdmin). ROLE_OWNER buni "🗑️
    // O'chirilganlar" sahifasida alohida ro'yxatda (kim/qachon arxivlagani
    // bilan) ko'rib turadi, xohlasa reclaimArchivedCourse orqali o'z
    // nomiga o'tkazib qaytadan tiklashi mumkin.
    @Transactional
    public void permanentlyDeleteCourse(Long courseId, User currentUser) {
        Course course = getAnyCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        if (course.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu kursni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
        }

        if (!currentUser.hasRole("ROLE_OWNER")) {
            course.setArchivedByAdmin(currentUser);
            course.setArchivedAt(LocalDateTime.now());
            courseRepository.save(course);
            return;
        }

        courseSectionProgressRepository.deleteBySection_Course_Id(courseId);
        courseSubscriptionRepository.deleteByCourse_Id(courseId);
        courseSectionRepository.deleteByCourse_Id(courseId);
        courseChapterRepository.deleteByCourse_Id(courseId);
        courseRepository.delete(course);
    }

    // Faqat ROLE_OWNER uchun — ADMIN'lar arxivlagan (permanentlyDeleteCourse)
    // kurslar ro'yxati, kim/qachon arxivlagani bilan.
    @Transactional(readOnly = true)
    public List<CourseArchivedByAdminDto> getAdminArchivedCourses(User currentUser) {
        if (!currentUser.hasRole("ROLE_OWNER")) {
            throw new AccessDeniedException("⛔ Faqat OWNER ko'ra oladi.");
        }
        return courseRepository.findArchivedByAdminOrderByArchivedAtDesc().stream()
                .map(c -> new CourseArchivedByAdminDto(c.getId(), c.getTitle(), c.getArchivedByAdmin().getUsername(), c.getArchivedAt()))
                .toList();
    }

    // "📤 O'zim nomimdan qayta nashr qilish" — faqat ROLE_OWNER, faqat
    // ADMIN arxivlagan kursga nisbatan: egasi (createdBy) OWNER'ning
    // o'ziga o'tkaziladi, arxiv/o'chirilgan belgilari tozalanadi (kurs
    // qaytadan ko'rinadigan bo'ladi). "published" ATAYLAB avtomatik
    // yoqilmaydi — OWNER buni alohida, o'zi xohlagan payt (odatiy
    // "✏️ Tahrirlash"/"Chop etish" tugmasi orqali) yoqadi.
    @Transactional
    public void reclaimArchivedCourse(Long courseId, User currentUser) {
        if (!currentUser.hasRole("ROLE_OWNER")) {
            throw new AccessDeniedException("⛔ Faqat OWNER qayta tiklashi mumkin.");
        }

        Course course = getAnyCourseOrThrow(courseId);
        if (course.getArchivedByAdmin() == null) {
            throw new IllegalArgumentException("❌ Bu kurs arxivlangan emas.");
        }

        course.setCreatedBy(currentUser);
        course.setArchivedByAdmin(null);
        course.setArchivedAt(null);
        course.setDeletedAt(null);
        courseRepository.save(course);
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

    // getCourseOrThrow + checkCanManage — boshqarish huquqi kerak bo'lgan,
    // lekin CourseService'ning o'zida joylashmagan boshqaruv amallari
    // uchun (masalan CourseWordExportService — butun kursni Word'ga
    // eksport qilish, faqat OWNER yoki shu kursni yaratgan ADMIN uchun).
    @Transactional(readOnly = true)
    public Course requireManageableCourse(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        return course;
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
        // Avval Mavzu (chapter) hal qilinadi — keyin dars bog'lanishida
        // (resolveLinkedTopic) shu Mavzu nomidan TEST BOSHQARUVI tomonida
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
        // Mavzu tanlansa/o'zgartirilsa — dars darhol o'sha mavzuga
        // "o'tib qoladi" (foydalanuvchi so'rovi bo'yicha); bo'sh
        // qoldirilsa — "Mavzusiz"ga qaytadi (unlink). Avval hal qilinadi —
        // keyin dars bog'lanishida (resolveLinkedTopic) shu Mavzu
        // nomidan TEST BOSHQARUVI tomonida ham mos TopicSection
        // yaratish/topish uchun foydalaniladi.
        CourseChapter chapter = resolveChapter(section.getCourse(), dto.chapterId(), dto.newChapterName());
        section.setChapter(chapter);
        section.setLinkedTopic(resolveLinkedTopic(dto.scienceName(), dto.topicName(), chapter));

        courseSectionRepository.save(section);
    }

    // Darsni "O'chirilganlar savati"ga o'tkazish (soft-delete)
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

    // "Kurs ichidan dars yoritmasi bo'yicha qidiruv" (topics.html va
    // courseDetail.js'da umumiy) — 1) berilgan darslar (topicIds — joriy
    // sahifadagi/kursdagi darslar) qaysi kurs(lar)ga bog'langanini
    // topadi; 2) shu kurs(lar)ning BARCHA (mavzu/chapter farqisiz) darsga
    // bog'langan darslari orasidan matn darsi (textContent — "dars
    // yoritmasi") ichida qidiruv so'zi bor darslarni qaytaradi. Bir
    // nechta kursga bog'langan bo'lsa — BARCHA o'sha kurslar qidiriladi.
    @Transactional(readOnly = true)
    public List<TopicExplanationSearchResultDto> searchTopicExplanations(List<Long> topicIds, String query) {
        if (topicIds == null || topicIds.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        List<Long> courseIds = courseSectionRepository.findCourseIdsByLinkedTopicIds(topicIds);
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return courseSectionRepository.searchLinkedExplanations(courseIds, query.trim());
    }

    // "♻️ Tiklash" — darsni savatdan qaytaradi, progress yozuvlari
    // avtomatik yana ko'rinadigan bo'ladi (ular hech qachon o'chirilmagan edi).
    @Transactional
    public void restoreSection(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        CourseSection section = getAnySectionOrThrow(sectionId, courseId);
        if (section.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu dars o'chirilmagan — tiklashning hojati yo'q.");
        }
        section.setDeletedAt(null);
        courseSectionRepository.save(section);
    }

    // "🗑️ Butunlay o'chirish" — FAQAT allaqachon savatda turgan darsga
    // nisbatan. QAYTARIB BO'LMAYDI: progress yozuvlari FK RESTRICT
    // bo'lgani uchun avval ular, keyin darsning o'zi o'chiriladi.
    @Transactional
    public void permanentlyDeleteSection(Long courseId, Long sectionId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        CourseSection section = getAnySectionOrThrow(sectionId, courseId);
        if (section.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu darsni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
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
            throw new IllegalArgumentException("❌Darslar ro'yxati kursning darslariga mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedSectionIds) {
            byId.get(id).setOrderIndex(index++);
        }
        courseSectionRepository.saveAll(sections);
    }

    // ADMIN/OWNER dars qo'shayotganda/tahrirlayotganda bo'lim+dars nomini
    // kiritsa — TEST BOSHQARUVI'da ular hali mavjud bo'lmasa avtomatik
    // yaratiladi (alohida TEST BOSHQARUVI sahifasiga o'tib, mos ID qidirib
    // yurishga hojat qolmaydi). Ikkalasi ham bo'sh bo'lsa — bog'lanish
    // olib tashlanadi (unlink).
    //
    // "chapter" — shu kurs darsi qaysi Mavzuga (CourseChapter) tegishli
    // bo'lsa, TEST BOSHQARUVI tomonida ham xuddi shu nomli Mavzu (TopicSection)
    // avtomatik topiladi/yaratiladi VA darsga biriktiriladi — bu YANGI
    // yaratilayotgan dars uchun ham, ALLAQACHON mavjud (kurs bilan
    // avvaldan bog'langan) dars uchun ham qo'llanadi. Kurs — HAR DOIM
    // "haqiqiy manba": kursda Mavzu o'zgartirilsa ("1-BOB"->"2-BOB"),
    // TEST BOSHQARUVIdagi darsning mavzusi ham shu bilan birga o'zgaradi;
    // kursda dars "Mavzusiz darslar"ga o'tkazilsa (chapter == null),
    // TEST BOSHQARUVIdagi dars ham "Mavzusiz"ga qaytariladi — ikki
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

    // "chapter.getName()" bilan bir xil nomli TopicSection shu BO'LIMDA mavjud
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
    // ikkala tomon (kurs Mavzusi VA test boshqaruvidagi Mavzu) sinxron
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

    // "chapterId" berilgan bo'lsa — ANIQ shu (mavjud) mavzu ishlatiladi
    // (frontend'da endi erkin matn emas, tanlov/select — shuning uchun
    // yozuvdagi kichik farq tufayli tasodifan yangi dublikat mavzu
    // yaratilib ketish muammosi endi yo'q). Aks holda "newChapterName"
    // berilsa (foydalanuvchi "➕ Yangi mavzu" variantini tanlagan) — shu
    // KURSDA shu nomli mavzu mavjud bo'lmasa avtomatik yaratiladi (oxiriga
    // qo'shiladi), mavjud bo'lsa (nom katta-kichik harfga sezgir emas)
    // o'shanga biriktiriladi — bu ikkinchi tekshiruv shunchaki xavfsizlik
    // to'ri, asosiy yo'l endi "chapterId" orqali. Ikkalasi ham bo'lmasa —
    // "Mavzusiz" (unlink).
    private CourseChapter resolveChapter(Course course, Long chapterId, String newChapterName) {
        if (chapterId != null) {
            return courseChapterRepository.findById(chapterId)
                    .filter(c -> c.getCourse().getId().equals(course.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("❌ Tanlangan mavzu topilmadi."));
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

    // Mavzu nomini o'zgartirish — CourseChapter BITTA umumiy yozuv bo'lgani
    // (har bir darsda alohida saqlangan matn emas) uchun, shu yerda bir
    // marta o'zgartirilishi bilan unga biriktirilgan BARCHA darslarda ham
    // (chapter_id FK orqali) darhol avtomatik yangilanadi — darslarni
    // birma-bir tahrirlab chiqish shart emas.
    @Transactional
    public void renameChapter(Long courseId, Long chapterId, String newName, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("❌ Mavzu nomi bo'sh bo'lishi mumkin emas.");
        }
        String trimmedNewName = newName.trim();

        CourseChapter chapter = courseChapterRepository.findById(chapterId)
                .filter(c -> c.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new NoSuchElementException("Mavzu topilmadi"));

        String oldName = chapter.getName();
        chapter.setName(trimmedNewName);
        courseChapterRepository.save(chapter);

        // MUHIM BOG'LANISH: resolveTopicSection() shu Mavzu nomi bilan
        // TEST BOSHQARUVI tomonida (Bo'lim ichida) xuddi shu nomli TopicSection
        // avtomatik yaratgan bo'lishi mumkin (kurs darsi Bo'lim/Mavzuga
        // bog'langanda). Bu ikkovi orasida to'g'ridan-to'g'ri FK yo'q —
        // bog'lanish faqat NOM orqali (bir tomonlama, yaratilish paytida).
        // Shu sabab, Mavzu shu yerda qayta nomlansa, TopicSection tomonda
        // ESKI nom bilan "yetim" qolib ketmasligi uchun — pastdagi
        // sinxronlash BIR YO'NALISHDA (kurs -> test boshqaruvi) shu yerda
        // avtomatik amalga oshiriladi. Aksincha (test boshqaruvida Mavzu
        // nomini o'zgartirish kursga qaytib ta'sir qilishi) — ATAYLAB
        // qilinmagan, chunki bitta TopicSection bir nechta har xil kursning
        // Mavzulariga mos kelib qolishi mumkin (umumiy, kursga bog'liq
        // bo'lmagan tushuncha).
        syncTopicSectionNamesForChapter(chapterId, oldName, trimmedNewName);
    }

    // "⬆⬇" — Mavzu "box"larini kurs sahifasida yuqoriga/pastga surish
    // (courseDetail.js). TopicService.reorderTopics bilan bir xil andoza —
    // frontend BUTUN (yangi tartibdagi) chapter id ro'yxatini yuboradi,
    // shu yerda ID to'plami kursning haqiqiy Mavzulariga mos kelishi
    // tekshiriladi, so'ng orderIndex 1'dan boshlab qayta yoziladi.
    @Transactional
    public void reorderChapters(Long courseId, List<Long> orderedChapterIds, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        List<CourseChapter> chapters = courseChapterRepository.findByCourse_IdOrderByOrderIndexAsc(courseId);
        Map<Long, CourseChapter> byId = new LinkedHashMap<>();
        for (CourseChapter c : chapters) {
            byId.put(c.getId(), c);
        }

        if (orderedChapterIds.size() != chapters.size() || !byId.keySet().containsAll(orderedChapterIds)) {
            throw new IllegalArgumentException("❌ Mavzular ro'yxati kursning mavzulariga mos kelmayapti.");
        }

        int index = 1;
        for (Long id : orderedChapterIds) {
            byId.get(id).setOrderIndex(index++);
        }
        courseChapterRepository.saveAll(chapters);
    }

    // renameChapter() tomonidan chaqiriladi — batafsili izoh o'sha yerda.
    private void syncTopicSectionNamesForChapter(Long chapterId, String oldName, String newName) {
        if (oldName.equalsIgnoreCase(newName)) {
            return; // Faqat registr (katta-kichik harf) o'zgargan — TopicSection nomi baribir bir xil hisoblanadi (ignoreCase), tegishga hojat yo'q.
        }

        List<CourseSection> linkedSections = courseSectionRepository.findByChapter_IdAndLinkedTopicIsNotNull(chapterId);

        // Bir nechta kurs darsi xuddi shu TopicSection'ga ishora qilishi
        // mumkin (masalan bir nechta dars bitta Bo'lim ichida) — har birini
        // FAQAT BIR MARTA qayta nomlash uchun.
        Set<Long> processedSectionIds = new HashSet<>();

        for (CourseSection cs : linkedSections) {
            Topic topic = cs.getLinkedTopic();
            TopicSection section = topic.getSection();
            if (section == null || !processedSectionIds.add(section.getId())) {
                continue;
            }
            // Admin TEST BOSHQARUVI tomonida shu mavzuni qo'lda BOSHQA
            // nomga o'zgartirgan bo'lishi mumkin — bunday holda avtomatik
            // qayta nomlash SHART EMAS (endi u kurs Mavzusi bilan bog'liq
            // emas, o'z holicha boshqariladi).
            if (!section.getName().equalsIgnoreCase(oldName)) {
                continue;
            }

            // TopicSection jadvalida UNIQUE(science_id, name) cheklovi bor —
            // agar shu Bo'limda YANGI nom bilan mavzu allaqachon mavjud bo'lsa,
            // nomni o'zgartirish o'rniga dars O'SHA mavjud mavzuga qayta
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

    // Kurs Mavzulari bilan TEST BOSHQARUVIdagi Mavzu (TopicSection)
    // bog'lanishini QO'LDA majburiy sinxronlashtirish — "🔄 Mavzu nomlarini
    // TEST BOSHQARUVI bilan sinxronlash" tugmasi shu orqali ishlaydi. Odatda bu
    // avtomatik sodir bo'ladi (har safar kurs darsi saqlanganda —
    // resolveLinkedTopic), lekin vaqt o'tishi bilan ikki tomon orasida
    // farq paydo bo'lishi mumkin (masalan dars shu avtomatik
    // sinxronlash qo'shilishidan OLDIN bog'langan bo'lsa, yoki bir nechta
    // Mavzu bir xil nomli TopicSection'ni "bo'lishib" ishlatgan bo'lsa —
    // birining nomini o'zgartirish ikkinchisiga ham "sirg'alib" ta'sir
    // qilishi mumkin). Shu metod HAR BIR kurs darsini joriy Mavzu
    // nomiga qarab qayta tekshirib, kerak bo'lsa to'g'rilaydi — SHU
    // JUMLADAN "Mavzusiz darslar"ga o'tkazilgan darslar ham (ular
    // TEST BOSHQARUVIDA ham Mavzusiz qilinadi, resolveLinkedTopic bilan
    // bir xil qoida).
    // Kursning BARCHA Mavzulari (hozircha bo'sh — hech qanday darsga
    // biriktirilmaganlari ham) — courseDetail.js'dagi Mavzu tanlash
    // select'ini to'liq to'ldirish uchun (shu jumladan bo'sh mavzularni
    // o'chirish imkoniyati bilan birga ko'rsatish).
    @Transactional(readOnly = true)
    public List<CourseChapterDto> getChapters(Long courseId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);
        return courseChapterRepository.findByCourseIdWithSectionCount(courseId);
    }

    // "➕ Mavzu qo'shish" (courseDetail.js) — avval Mavzu FAQAT bitta
    // dars qo'shish/tahrirlash payti ("➕ Yangi mavzu yaratish..."
    // varianti orqali) yaratilar edi. Endi to'g'ridan-to'g'ri, darssiz
    // (bo'sh) yaratish ham mumkin — foydalanuvchi avval mavzu tuzilmasini
    // qurib, keyin har biriga alohida dars qo'shishni xohlaydi.
    @Transactional
    public CourseChapterDto createChapter(Long courseId, String name, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("❌ Mavzu nomini kiriting.");
        }
        String trimmed = name.trim();

        if (courseChapterRepository.findByCourse_IdAndNameIgnoreCase(courseId, trimmed).isPresent()) {
            throw new IllegalArgumentException("❌ Bu nomli mavzu allaqachon mavjud.");
        }

        int nextOrder = courseChapterRepository.findTopByCourse_IdOrderByOrderIndexDesc(courseId)
                .map(c -> c.getOrderIndex() + 1)
                .orElse(1);

        CourseChapter chapter = courseChapterRepository.save(CourseChapter.builder()
                .course(course)
                .name(trimmed)
                .orderIndex(nextOrder)
                .build());

        return new CourseChapterDto(chapter.getId(), chapter.getName(), chapter.getOrderIndex(), 0L);
    }

    // Faqat BO'SH (hech qanday darsga biriktirilmagan) Mavzuni
    // o'chirishga ruxsat beradi — "🗑️" tugmasi (courseDetail.js, Mavzu
    // tanlash select'i yonida). Foydali darslar bilan band bo'lgan
    // Mavzuni tasodifan o'chirib, ularni "yetim" qoldirmaslik uchun.
    @Transactional
    public void deleteChapter(Long courseId, Long chapterId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        CourseChapter chapter = courseChapterRepository.findById(chapterId)
                .filter(c -> c.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new NoSuchElementException("Mavzu topilmadi"));

        if (courseSectionRepository.existsByChapter_Id(chapterId)) {
            throw new IllegalArgumentException(
                    "❌ Bu mavzuda darslar bor — avval ularni boshqa mavzuga o'tkazing yoki Mavzusiz qiling.");
        }

        courseChapterRepository.delete(chapter);
    }

    // "🗑️ Mavzu + darslarni birga o'chirish" — FAQAT shu yerdan (kurs
    // ichidan) ishlaydi, TEST BOSHQARUVIdan (topicSections.html) EMAS —
    // foydalanuvchi so'rovi bo'yicha ataylab shu yerga joylashtirilgan
    // (chunki bu amal aynan KURS Mavzusi kontekstida ma'noga ega).
    //
    // deleteChapter'dan farqli, bo'sh bo'lishi SHART EMAS: shu Mavzudagi
    // BARCHA kurs darslarini (CourseSection) soft-delete qiladi. MUHIM
    // QOIDA (foydalanuvchi ANIQ talabi): TEST BOSHQARUVIdagi Topic/Question
    // HECH QACHON, HECH QANDAY holatda bu yerdan tegilmaydi/o'chirilmaydi —
    // agar shu Mavzudagi biror dars TEST BOSHQARUVIga bog'langan bo'lsa,
    // FAQAT bog'lanishning o'zi uziladi (bu allaqachon avtomatik sodir
    // bo'ladi: CourseSection soft-delete qilingach, uning linkedTopic FK'i
    // bazada jismoniy qolsa ham, findByLinkedTopic_Id/findLinkedCourseTitles*
    // kabi BARCHA "bog'langanmi?" so'rovlari deletedAt IS NULL filtri bilan
    // yozilgan — shu sabab dars darhol "bog'lanmagan" ko'rinadi, Topic/
    // Question esa TEST BOSHQARUVIda bus-butun, tegilmagan holda qolaveradi).
    @Transactional
    public void deleteChapterWithLinkedTopics(Long courseId, Long chapterId, User currentUser) {
        Course course = getCourseOrThrow(courseId);
        checkCanManage(course, currentUser);

        CourseChapter chapter = courseChapterRepository.findById(chapterId)
                .filter(c -> c.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new NoSuchElementException("Mavzu topilmadi"));

        List<CourseSection> chapterSections = courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(courseId).stream()
                .filter(s -> s.getChapter() != null && s.getChapter().getId().equals(chapterId))
                .toList();

        // MUHIM: Mavzu (chapter) HARD-DELETE qilinadi — shu sabab har bir
        // darsdagi "chapter" bog'lanishi OLDINDAN null qilinishi SHART
        // (soft-delete qilingandan keyin ham). Aks holda darslar hali
        // o'chirilayotgan Mavzuga ishora qilib turgan holda flush
        // bo'lib, Hibernate "TransientPropertyValueException ...
        // references an unsaved transient instance" xatosini berardi —
        // haqiqiy production bug (mavzu o'chirishga urinishda foydalanuvchi
        // shu xom Hibernate xatosini ko'rgan). Null qilingandan keyin
        // darslar (agar keyinchalik tiklansa) "Mavzusiz darslar"
        // sifatida ko'rinadi — bu allaqachon tanish, qo'llab-quvvatlanadigan holat.
        LocalDateTime now = LocalDateTime.now();
        chapterSections.forEach(s -> {
            s.setDeletedAt(now);
            s.setChapter(null);
        });
        courseSectionRepository.saveAll(chapterSections);
        courseChapterRepository.delete(chapter);
    }

    // "🗑️ Bo'sh mavzularni o'chirish" — shu kursda hech qanday darsga
    // biriktirilmagan (sectionCount==0) BARCHA Mavzularni bir yo'la
    // o'chiradi (deleteChapter'dagi bilan bir xil xavfsizlik qoidasi —
    // faqat bo'sh mavzular, band bo'lganlariga tegilmaydi).
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
                continue; // Bo'lim/Darsga umuman bog'lanmagan — sinxronlanadigan narsa yo'q.
            }

            // chapter == null (kurs darsi "Mavzusiz darslar"da) bo'lsa —
            // "to'g'ri" holat ham aynan shu: dars TEST BOSHQARUVIDA ham
            // Mavzusiz bo'lishi kerak (null). resolveLinkedTopic bilan
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
            throw new IllegalArgumentException("❌Dars nomi bo'sh bo'lishi mumkin emas.");
        }

        if (dto.title().trim().length() > SECTION_TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("❌Dars nomi juda uzun (ko'pi bilan "
                    + SECTION_TITLE_MAX_LENGTH + " ta belgi bo'lishi kerak).");
        }

        CourseSectionType type;
        try {
            type = CourseSectionType.valueOf(dto.type());
        } catch (Exception e) {
            throw new IllegalArgumentException("❌Dars turi noto'g'ri (TEXT, VIDEO yoki MIXED bo'lishi kerak).");
        }

        // MIXED — darsda ham matn, ham video bo'lgani uchun ikkala tekshiruv
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
    // uni oddiy ko'rish/tahrirlash/dars qo'shish kabi amallar orqali
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
            throw new NoSuchElementException("Dars topilmadi");
        }
        return section;
    }

    // FAQAT "O'chirilganlar savati" amallari (restoreSection,
    // permanentlyDeleteSection, getDeletedSections) uchun — soft-delete
    // qilingan darsni ham topa oladi.
    private CourseSection getAnySectionOrThrow(Long sectionId, Long courseId) {
        CourseSection section = courseSectionRepository.findById(sectionId)
                .orElseThrow(() -> new NoSuchElementException("Dars topilmadi"));

        if (!section.getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("❌Dars bu kursga tegishli emas.");
        }

        return section;
    }

    private CourseDto toDto(Course course, User currentUser) {
        int sectionCount = (int) courseSectionRepository.countByCourse_Id(course.getId());
        int chapterCount = (int) courseChapterRepository.countByCourse_Id(course.getId());
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
                .chapterCount(chapterCount)
                .subscribed(subscribed)
                .fieldId(course.getField() != null ? course.getField().getId() : null)
                .fieldName(course.getField() != null ? course.getField().getName() : null)
                .orderIndex(course.getOrderIndex())
                .createdAt(course.getCreatedAt())
                .deletedAt(course.getDeletedAt())
                .build();
    }

    /* ================= "🔗 Darsga havola" tekshiruvi ================= */

    // "🔗 Havolalarni tekshirish" (courseDetail.js) — shu kursga bog'langan
    // har bir darsning barcha (faol) savollari to'g'ri javob izohida
    // O'ZINING darsiga havola bor-yo'qligini, bor bo'lsa TO'G'RI
    // ekanini tekshiradi. Faqat KO'RSATISH uchun — hech narsa o'zgartirmaydi.
    @Transactional(readOnly = true)
    public List<TopicLinkAuditDto> auditTopicLinks(Long courseId) {
        List<CourseSection> linkedSections = courseSectionRepository.findByCourse_IdAndLinkedTopicIsNotNull(courseId);

        List<TopicLinkAuditDto> result = new ArrayList<>();
        for (CourseSection section : linkedSections) {
            Topic topic = section.getLinkedTopic();
            String expectedHref = "/courses/" + courseId + "/sections/" + section.getId();

            List<Question> questions = questionRepository.getQuestionsByTopicId(topic.getId());

            int ok = 0;
            int missing = 0;
            List<TopicLinkAuditItemDto> wrong = new ArrayList<>();

            for (Question q : questions) {
                Answer trueAnswer = findTrueAnswer(q);
                String commentary = trueAnswer != null ? trueAnswer.getCommentary() : null;
                Matcher m = commentary != null ? TOPIC_LINK_HREF_PATTERN.matcher(commentary) : null;

                if (m == null || !m.find()) {
                    missing++;
                    continue;
                }

                String actualHref = "/courses/" + m.group(1) + "/sections/" + m.group(2);
                if (actualHref.equals(expectedHref)) {
                    ok++;
                } else {
                    wrong.add(new TopicLinkAuditItemDto(
                            q.getId(),
                            truncateForSnippet(q.getQuestionText()),
                            actualHref,
                            expectedHref
                    ));
                }
            }

            result.add(new TopicLinkAuditDto(topic.getId(), topic.getName(), expectedHref, ok, missing, wrong));
        }
        return result;
    }

    // "➕ Havola qo'shish" — shu darsdagi savollar orasidan izohida HECH
    // QANDAY dars havolasi yo'qlariga to'g'ri havolani qo'shadi (mavjud
    // havolasi bor savollarga — to'g'ri bo'lsin, noto'g'ri bo'lsin —
    // TEGILMAYDI, ular uchun alohida "✅ To'g'irlash" — fixWrongTopicLink).
    // Qaytariladigan son — nechta savolga qo'shilgani.
    @Transactional
    public int addMissingTopicLinks(Long courseId, Long topicId) {
        CourseSection section = courseSectionRepository.findByCourse_IdAndLinkedTopic_Id(courseId, topicId)
                .orElseThrow(() -> new IllegalArgumentException("❌ Bu dars shu kursga bog'lanmagan."));
        return addMissingLinksForSection(courseId, section);
    }

    // "➕ Barchasiga havola qo'shish" — auditTopicLinks bilan bir xil
    // ko'lamda (shu kursga bog'langan HAMMA darslar), har birida
    // topilgan (izohida HECH QANDAY dars havolasi yo'q) savollarga
    // to'g'ri havolani bir yo'la qo'shadi — fixAllWrongTopicLinksInCourse
    // bilan bir xil andoza (haqiqiy holat: yangi qo'shilgan yuzlab
    // dars/savolda havola umuman yo'q edi, birma-bir "➕ Havola
    // qo'shish"ni bosib chiqish o'ninchi darsdan keyin amaliy emas).
    @Transactional
    public int addAllMissingTopicLinksInCourse(Long courseId) {
        List<CourseSection> linkedSections = courseSectionRepository.findByCourse_IdAndLinkedTopicIsNotNull(courseId);
        int total = 0;
        for (CourseSection section : linkedSections) {
            total += addMissingLinksForSection(courseId, section);
        }
        return total;
    }

    private int addMissingLinksForSection(Long courseId, CourseSection section) {
        List<Question> questions = questionRepository.getQuestionsByTopicId(section.getLinkedTopic().getId());
        int fixed = 0;
        for (Question q : questions) {
            Answer trueAnswer = findTrueAnswer(q);
            if (trueAnswer == null) continue;

            String commentary = trueAnswer.getCommentary();
            boolean hasLink = commentary != null && TOPIC_LINK_HREF_PATTERN.matcher(commentary).find();
            if (hasLink) continue;

            // applyCorrectLink (strip+qo'shish) ATAYLAB ishlatilgan — oddiy
            // qo'shish (append) o'rniga: agar izohda TOPIC_LINK_HREF_PATTERN
            // aniqlay olmaydigan, lekin qisman/buzuq havola qoldig'i bo'lsa
            // ham (masalan eski formatdagi), shu yerda tozalanadi — ikkita
            // havola bir joyda qolib ketmasligi uchun (haqiqiy topilgan bug).
            applyCorrectLink(trueAnswer, courseId, section);
            fixed++;
        }
        return fixed;
    }

    // "✅ To'g'irlash" — BITTA savolning izohidagi (boshqa darsga
    // bog'langan, NOTO'G'RI) havola belgisini olib tashlab, o'rniga
    // O'ZINING darsiga TO'G'RI havolani qo'yadi.
    @Transactional
    public void fixWrongTopicLink(Long courseId, Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("❌ Savol topilmadi."));

        CourseSection section = courseSectionRepository
                .findByCourse_IdAndLinkedTopic_Id(courseId, question.getTopic().getId())
                .orElseThrow(() -> new IllegalArgumentException("❌ Bu savolning darsi shu kursga bog'lanmagan."));

        Answer trueAnswer = findTrueAnswer(question);
        if (trueAnswer == null) {
            throw new IllegalArgumentException("❌ Bu savolning to'g'ri javobi topilmadi.");
        }

        applyCorrectLink(trueAnswer, courseId, section);
    }

    // "✅ Barchasini to'g'irlash" — BITTA darsdagi barcha XATO havolali
    // savollarni bitta so'rovda to'g'irlaydi (havolasi umuman yo'qlariga
    // tegmaydi — ular addMissingTopicLinks orqali). Qaytariladigan son —
    // nechta savol to'g'irlangani.
    @Transactional
    public int fixAllWrongTopicLinks(Long courseId, Long topicId) {
        CourseSection section = courseSectionRepository.findByCourse_IdAndLinkedTopic_Id(courseId, topicId)
                .orElseThrow(() -> new IllegalArgumentException("❌ Bu dars shu kursga bog'lanmagan."));
        return fixAllWrongLinksForSection(courseId, section);
    }

    // "🛠️ Butun kursdagi BARCHA xato havolalarni tuzatish" — auditTopicLinks
    // bilan bir xil ko'lamda (shu kursga bog'langan HAMMA darslar), har
    // birida topilgan xato havolalarni birma-bir to'g'irlaydi. Katta
    // kurslarda yuzlab xato havola bir yo'la topilishi mumkin (real holat:
    // Bakteriologiya kursida 51 ta darsda 780 ta xato havola topilgan edi) —
    // shu sabab alohida (per-topic emas, butun kurs) tugma qo'shilgan.
    @Transactional
    public int fixAllWrongTopicLinksInCourse(Long courseId) {
        List<CourseSection> linkedSections = courseSectionRepository.findByCourse_IdAndLinkedTopicIsNotNull(courseId);
        int total = 0;
        for (CourseSection section : linkedSections) {
            total += fixAllWrongLinksForSection(courseId, section);
        }
        return total;
    }

    // "🧹 Takroriy havolalarni tozalash" — TOPIC_LINK_HREF_PATTERN eski
    // (to'liq URL) formatni tanimasligi sabab (endi tuzatilgan — lekin
    // shu bug tufayli "➕ Havola qo'shish" avval ISHLATILGAN bo'lsa),
    // ba'zi savollarning izohida IKKITA (yoki ko'proq) havola belgisi
    // qolib ketgan bo'lishi mumkin — ikkalasi ham TO'G'RI joyga
    // bog'langan bo'lsa ham, auditTopicLinks buni alohida ko'rsatmaydi
    // (faqat BIRINCHI topilgan havolani tekshiradi, "OK" deb hisoblaydi).
    // Bu metod BARCHA darslarni ko'rib, havolasi (hech bo'lmasa bitta)
    // bor BARCHA savollarni qayta normallashtiradi (strip + qayta
    // qo'shish) — shu bilan bir yo'la ham takroriy, ham (avvalgi
    // TOPIC_LINK_BADGE_PATTERN xatosi tufayli yuzaga kelishi mumkin
    // bo'lgan) "yetim" (buzuq, yopilmagan) <span> qoldiqlarini ham
    // tozalaydi — count>=1 shart, faqat >1 emas, chunki buzuq
    // qoldiqlarda ba'zan bitta haqiqiy href qolib, qolgani "yetim"
    // bo'lib turishi mumkin.
    @Transactional
    public int dedupeTopicLinksInCourse(Long courseId) {
        List<CourseSection> linkedSections = courseSectionRepository.findByCourse_IdAndLinkedTopicIsNotNull(courseId);
        int total = 0;
        for (CourseSection section : linkedSections) {
            List<Question> questions = questionRepository.getQuestionsByTopicId(section.getLinkedTopic().getId());
            for (Question q : questions) {
                Answer trueAnswer = findTrueAnswer(q);
                if (trueAnswer == null) continue;

                String commentary = trueAnswer.getCommentary();
                if (commentary == null) continue;

                if (!TOPIC_LINK_HREF_PATTERN.matcher(commentary).find()) continue;

                applyCorrectLink(trueAnswer, courseId, section);
                total++;
            }
        }
        return total;
    }

    // fixAllWrongTopicLinks va fixAllWrongTopicLinksInCourse'ning umumiy
    // ichki qismi — bitta dars (allaqachon topilgan CourseSection bilan)
    // doirasidagi barcha xato havolalarni to'g'irlaydi.
    private int fixAllWrongLinksForSection(Long courseId, CourseSection section) {
        Topic topic = section.getLinkedTopic();
        String expectedHref = "/courses/" + courseId + "/sections/" + section.getId();

        List<Question> questions = questionRepository.getQuestionsByTopicId(topic.getId());
        int fixed = 0;
        for (Question q : questions) {
            Answer trueAnswer = findTrueAnswer(q);
            if (trueAnswer == null) continue;

            String commentary = trueAnswer.getCommentary();
            if (commentary == null) continue;

            Matcher m = TOPIC_LINK_HREF_PATTERN.matcher(commentary);
            if (!m.find()) continue; // havola umuman yo'q — bu yerga tegishli emas

            String actualHref = "/courses/" + m.group(1) + "/sections/" + m.group(2);
            if (actualHref.equals(expectedHref)) continue; // allaqachon to'g'ri

            applyCorrectLink(trueAnswer, courseId, section);
            fixed++;
        }
        return fixed;
    }

    // Bitta javobning izohidagi (bor bo'lsa) eski dars havola belgisini
    // olib tashlab, o'rniga TO'G'RI havolani qo'yadi — fixWrongTopicLink
    // va fixAllWrongLinksForSection ikkalasi ham shundan foydalanadi.
    private void applyCorrectLink(Answer trueAnswer, Long courseId, CourseSection section) {
        String correctBadge = buildTopicLinkBadge(courseId, section.getId(), section.getLinkedTopic().getName());
        String existing = trueAnswer.getCommentary();
        String cleaned = existing == null ? "" : TOPIC_LINK_BADGE_PATTERN.matcher(existing).replaceAll("");
        trueAnswer.setCommentary(cleaned + correctBadge);
    }

    private Answer findTrueAnswer(Question question) {
        if (question.getAnswers() == null) return null;
        return question.getAnswers().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsTrue()))
                .findFirst()
                .orElse(null);
    }

    private String truncateForSnippet(String text) {
        if (text == null) return "";
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }

    // topicLinkButton.js#buildTopicLinkHtml bilan AYNAN bir xil HTML/uslub
    // — ikkalasi ham sinxron saqlanishi kerak (birida o'zgarish bo'lsa,
    // ikkinchisida ham qo'lda yangilanishi kerak).
    private String buildTopicLinkBadge(Long courseId, Long sectionId, String topicName) {
        String url = "/courses/" + courseId + "/sections/" + sectionId;
        String safeTitle = topicName.replace("\"", "&quot;");
        return " <span style=\"display:inline-block;margin-top:6px;padding:4px 10px 4px 8px;" +
                "background:#e8f5f3;border-left:3px solid #00796b;border-radius:4px;" +
                "color:#00695c;font-weight:600;font-style:normal;text-decoration:none\">" +
                "📖 <a href=\"" + url + "\" style=\"color:#00695c;text-decoration:underline\">\"" +
                safeTitle + "\" darsini kursda o'qish</a></span>";
    }
}
