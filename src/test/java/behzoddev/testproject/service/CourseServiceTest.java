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
import behzoddev.testproject.dto.course.CourseChapterDto;
import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSaveDto;
import behzoddev.testproject.dto.course.CourseSectionContentDto;
import behzoddev.testproject.dto.course.CourseSectionSaveDto;
import behzoddev.testproject.dto.course.CourseSectionSummaryDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseChapter;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.CourseSectionType;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Kurs bo'limlarining ketma-ket ochilish mantig'i (N-bo'lim faqat N-1
 * tugatilgach ochiladi) va OWNER/obunachi ruxsat farqlari — asosiy e'tibor
 * shu yerda.
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseFieldRepository courseFieldRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private CourseChapterRepository courseChapterRepository;
    @Mock
    private CourseSubscriptionRepository courseSubscriptionRepository;
    @Mock
    private CourseSectionProgressRepository courseSectionProgressRepository;
    @Mock
    private ScienceRepository scienceRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicSectionRepository topicSectionRepository;
    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private CourseService courseService;

    private User subscriber() {
        return User.builder().id(1L).username("student").roles(new HashSet<>(Set.of(
                Role.builder().id(1L).roleName("ROLE_USER").build()))).build();
    }

    private User owner() {
        return User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_OWNER").build()))).build();
    }

    // ===== getSectionContent (ketma-ket ochilish) =====

    @Test
    void getSectionContent_firstSection_alwaysUnlockedForSubscriber() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section1 = CourseSection.builder().id(1L).course(course).title("1-bo'lim")
                .orderIndex(1).type(CourseSectionType.TEXT).textContent("matn").build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(1L)).thenReturn(Optional.of(section1));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 0)).thenReturn(Optional.empty());
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 2)).thenReturn(Optional.empty());
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section1));

        CourseSectionContentDto result = courseService.getSectionContent(1L, 1L, user);

        assertThat(result.title()).isEqualTo("1-bo'lim");
    }

    @Test
    void getSectionContent_secondSectionWithoutCompletingFirst_throwsAccessDenied() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section2 = CourseSection.builder().id(2L).course(course).title("2-bo'lim")
                .orderIndex(2).type(CourseSectionType.TEXT).textContent("matn").build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(2L)).thenReturn(Optional.of(section2));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        CourseSection section1 = CourseSection.builder().id(1L).course(course).orderIndex(1).build();
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section1, section2));

        assertThatThrownBy(() -> courseService.getSectionContent(1L, 2L, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getSectionContent_secondSectionAfterCompletingFirst_isUnlocked() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section2 = CourseSection.builder().id(2L).course(course).title("2-bo'lim")
                .orderIndex(2).type(CourseSectionType.TEXT).textContent("matn").build();
        CourseSection section1 = CourseSection.builder().id(1L).course(course).orderIndex(1).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(2L)).thenReturn(Optional.of(section2));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 1)).thenReturn(Optional.of(section1));
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(true); // 1-bo'lim tugatilgan
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 2L)).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 3)).thenReturn(Optional.empty());
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section1, section2));

        CourseSectionContentDto result = courseService.getSectionContent(1L, 2L, user);

        assertThat(result.title()).isEqualTo("2-bo'lim");
    }

    @Test
    void getSectionContent_notSubscribed_throwsAccessDenied() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section1 = CourseSection.builder().id(1L).course(course).orderIndex(1)
                .type(CourseSectionType.TEXT).textContent("matn").build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(1L)).thenReturn(Optional.of(section1));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);

        assertThatThrownBy(() -> courseService.getSectionContent(1L, 1L, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getSectionContent_owner_bypassesLockRegardlessOfSubscription() {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();
        CourseSection section2 = CourseSection.builder().id(2L).course(course).title("2-bo'lim")
                .orderIndex(2).type(CourseSectionType.TEXT).textContent("matn").build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(2L)).thenReturn(Optional.of(section2));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(99L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(99L, 2L)).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 1)).thenReturn(Optional.empty());
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 3)).thenReturn(Optional.empty());

        CourseSectionContentDto result = courseService.getSectionContent(1L, 2L, owner);

        assertThat(result.title()).isEqualTo("2-bo'lim");
    }

    // ===== Bo'limlar (CourseChapter) bir-biridan MUSTAQIL ochiladi =====
    // (foydalanuvchi so'rovi bo'yicha, 2026-09-03: "kurs bo'limlari bir
    // biriga bog'liq emas, barcha bo'limlarni birinchi mavzusi ochiq
    // bo'lsin" — avval BUTUN kurs bo'ylab bitta ketma-ket zanjir edi,
    // 2-Bo'limning 1-mavzusi ham 1-Bo'limni to'liq tugatishni talab
    // qilardi).

    @Test
    void getSectionContent_secondChapterFirstSection_unlockedWithoutCompletingFirstChapter() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter chapter1 = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseChapter chapter2 = CourseChapter.builder().id(20L).course(course).name("2-bob").orderIndex(2).build();
        CourseSection s1 = CourseSection.builder().id(1L).course(course).chapter(chapter1).orderIndex(1)
                .type(CourseSectionType.TEXT).textContent("matn").build();
        CourseSection s2 = CourseSection.builder().id(2L).course(course).chapter(chapter1).orderIndex(2)
                .type(CourseSectionType.TEXT).textContent("matn").build();
        CourseSection s3 = CourseSection.builder().id(3L).course(course).chapter(chapter2).title("2-bob, 1-mavzu")
                .orderIndex(3).type(CourseSectionType.TEXT).textContent("matn").build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(3L)).thenReturn(Optional.of(s3));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        // 1-Bob HALI tugatilmagan (s1/s2 uchun progress yo'q) — shunga
        // qaramay 2-Bobning 1-mavzusi (s3) ochiq bo'lishi kerak.
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(s1, s2, s3));
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 3L)).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 2)).thenReturn(Optional.of(s2));
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 4)).thenReturn(Optional.empty());

        CourseSectionContentDto result = courseService.getSectionContent(1L, 3L, user);

        assertThat(result.title()).isEqualTo("2-bob, 1-mavzu");
    }

    @Test
    void getSectionContent_secondSectionInSameChapter_stillRequiresFirstCompleted() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter chapter1 = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseSection s1 = CourseSection.builder().id(1L).course(course).chapter(chapter1).orderIndex(1).build();
        CourseSection s2 = CourseSection.builder().id(2L).course(course).chapter(chapter1)
                .orderIndex(2).type(CourseSectionType.TEXT).textContent("matn").build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(2L)).thenReturn(Optional.of(s2));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(s1, s2));
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(false);

        // BIR XIL Bo'lim ichida ketma-ketlik hali ham saqlanishi kerak.
        assertThatThrownBy(() -> courseService.getSectionContent(1L, 2L, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getDetail_secondChapterFirstSection_notLockedEvenIfFirstChapterIncomplete() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").published(true).createdBy(owner()).build();
        CourseChapter chapter1 = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseChapter chapter2 = CourseChapter.builder().id(20L).course(course).name("2-bob").orderIndex(2).build();
        CourseSection s1 = CourseSection.builder().id(1L).course(course).chapter(chapter1).orderIndex(1)
                .type(CourseSectionType.TEXT).build();
        CourseSection s2 = CourseSection.builder().id(2L).course(course).chapter(chapter2).orderIndex(2)
                .type(CourseSectionType.TEXT).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                anyLong(), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(s1, s2));
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(anyLong(), anyLong())).thenReturn(false);

        CourseDetailDto result = courseService.getDetail(1L, user);

        CourseSectionSummaryDto s1Dto = result.sections().stream().filter(s -> s.id().equals(1L)).findFirst().orElseThrow();
        CourseSectionSummaryDto s2Dto = result.sections().stream().filter(s -> s.id().equals(2L)).findFirst().orElseThrow();
        assertThat(s1Dto.locked()).isFalse(); // 1-Bobning 1-mavzusi
        assertThat(s2Dto.locked()).isFalse(); // 2-Bobning 1-mavzusi — 1-Bob tugallanmagan bo'lsa ham ochiq
    }

    // ===== markSectionCompleted =====

    @Test
    void markSectionCompleted_unlockedSection_savesProgress() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section1 = CourseSection.builder().id(1L).course(course).orderIndex(1)
                .type(CourseSectionType.TEXT).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(1L)).thenReturn(Optional.of(section1));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section1));

        courseService.markSectionCompleted(1L, 1L, user);

        org.mockito.Mockito.verify(courseSectionProgressRepository).save(any());
    }

    @Test
    void markSectionCompleted_alreadyCompleted_isIdempotentNoDoubleSave() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section1 = CourseSection.builder().id(1L).course(course).orderIndex(1)
                .type(CourseSectionType.TEXT).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(1L)).thenReturn(Optional.of(section1));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                eq(1L), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(true);
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(true);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section1));

        courseService.markSectionCompleted(1L, 1L, user);

        org.mockito.Mockito.verify(courseSectionProgressRepository, org.mockito.Mockito.never()).save(any());
    }

    // ===== getDetail =====

    @Test
    void getDetail_unpublishedCourse_notOwner_throwsNotFound() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Qoralama").published(false).createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.getDetail(1L, user))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getDetail_unpublishedCourse_owner_stillVisible() {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Qoralama").published(false).createdBy(owner).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
                anyLong(), eq(1L), eq(CourseSubscriptionStatus.CONFIRMED), any())).thenReturn(false);
        when(courseSubscriptionRepository.existsByUser_IdAndCourse_IdAndStatus(
                anyLong(), eq(1L), eq(CourseSubscriptionStatus.PENDING))).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

        CourseDetailDto result = courseService.getDetail(1L, owner);

        assertThat(result.canManage()).isTrue();
        assertThat(result.subscribed()).isTrue(); // OWNER har doim "subscribed" hisoblanadi
    }

    // ===== createCourse / updateCourse validation =====

    @Test
    void addSection_blankTitle_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSectionSaveDto dto = new CourseSectionSaveDto(" ", "TEXT", "matn", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dars nomi bo'sh");
    }

    // Haqiqiy production bug: bo'lim nomi 200 belgidan (eski ustun
    // uzunligi) oshib ketganda "Data too long for column 'title'" DB
    // xatosi chiqib, foydalanuvchiga FK xatolariga mo'ljallangan
    // chalg'ituvchi "bog'liq ma'lumotlar mavjud" xabari ko'rsatilardi.
    // Ustun 500 belgigacha kengaytirildi, endi aniq validatsiya bilan
    // oldindan tekshiriladi.
    @Test
    void addSection_titleTooLong_throwsWithAccurateMessage() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        String tooLongTitle = "a".repeat(501);
        CourseSectionSaveDto dto = new CourseSectionSaveDto(tooLongTitle, "TEXT", "matn", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("juda uzun");
    }

    @Test
    void addSection_titleExactly500Chars_isAllowed() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String maxTitle = "a".repeat(500);
        CourseSectionSaveDto dto = new CourseSectionSaveDto(maxTitle, "TEXT", "matn", null, null, null, null, null, null, null, null);

        CourseSectionSummaryDto result = courseService.addSection(1L, dto, owner());

        assertThat(result.title()).isEqualTo(maxTitle);
    }

    @Test
    void addSection_textTypeWithoutTextContent_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("Sarlavha", "TEXT", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Matn kontenti bo'sh");
    }

    @Test
    void addSection_videoTypeWithoutUrl_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("Sarlavha", "VIDEO", null, "YOUTUBE", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Video manba va URL");
    }

    @Test
    void addSection_validTextSection_assignsNextOrderIndex() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection last = CourseSection.builder().id(1L).course(course).orderIndex(2).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.of(last));
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("3-bo'lim", "TEXT", "matn", null, null, null, null, null, null, null, null);
        CourseSectionSummaryDto result = courseService.addSection(1L, dto, owner());

        assertThat(result.orderIndex()).isEqualTo(3);
    }

    @Test
    void addSection_firstSectionInCourse_orderIndexOne() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("1-bo'lim", "TEXT", "matn", null, null, null, null, null, null, null, null);
        CourseSectionSummaryDto result = courseService.addSection(1L, dto, owner());

        assertThat(result.orderIndex()).isEqualTo(1);
    }

    // ===== deleteSection: bo'lim boshqa kursga tegishli bo'lsa =====

    @Test
    void deleteSection_sectionBelongsToDifferentCourse_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        Course otherCourse = Course.builder().id(2L).title("Boshqa kurs").createdBy(owner()).build();
        CourseSection section = CourseSection.builder().id(1L).course(otherCourse).orderIndex(1).build();

        when(courseSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> courseService.deleteSection(1L, 1L, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bu kursga tegishli emas");
    }

    // ===== deleteCourse (soft-delete — "O'chirilganlar savati") =====
    // Haqiqiy production hodisa: bir bo'limni o'chirish deb butun kurs
    // qattiq (hard) o'chirilib ketgan edi — shu sabab deleteCourse endi
    // faqat deletedAt'ni belgilaydi, bog'liq yozuvlarga (progress/
    // obuna/bo'lim/mavzu) UMUMAN TEGMAYDI — instant, to'liq tiklash
    // (restoreCourse) uchun.

    @Test
    void deleteCourse_softDeletes_doesNotTouchDependentRecords() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.deleteCourse(1L, owner());

        assertThat(course.getDeletedAt()).isNotNull();
        org.mockito.Mockito.verify(courseRepository).save(course);
        org.mockito.Mockito.verifyNoInteractions(
                courseSectionProgressRepository, courseSubscriptionRepository, courseSectionRepository);
        org.mockito.Mockito.verify(courseRepository, org.mockito.Mockito.never()).delete(org.mockito.Mockito.any());
    }

    @Test
    void deleteCourse_courseNotFound_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deleteCourse(1L, owner()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteCourse_alreadySoftDeleted_treatedAsNotFound() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner())
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.deleteCourse(1L, owner()))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ===== restoreCourse =====

    @Test
    void restoreCourse_clearsDeletedAt() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner())
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.restoreCourse(1L, owner());

        assertThat(course.getDeletedAt()).isNull();
        org.mockito.Mockito.verify(courseRepository).save(course);
    }

    @Test
    void restoreCourse_notDeleted_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.restoreCourse(1L, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o'chirilmagan");
    }

    // ===== permanentlyDeleteCourse =====
    // Ikki bosqichli himoya: faqat allaqachon "savat"da (deletedAt != null)
    // turgan kursga nisbatan ishlaydi, aks holda rad etiladi.

    @Test
    void permanentlyDeleteCourse_softDeletedCourse_cascadesAndDeletes() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner())
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.permanentlyDeleteCourse(1L, owner());

        var inOrder = org.mockito.Mockito.inOrder(
                courseSectionProgressRepository, courseSubscriptionRepository,
                courseSectionRepository, courseChapterRepository, courseRepository);
        inOrder.verify(courseSectionProgressRepository).deleteBySection_Course_Id(1L);
        inOrder.verify(courseSubscriptionRepository).deleteByCourse_Id(1L);
        inOrder.verify(courseSectionRepository).deleteByCourse_Id(1L);
        inOrder.verify(courseChapterRepository).deleteByCourse_Id(1L);
        inOrder.verify(courseRepository).delete(course);
    }

    @Test
    void permanentlyDeleteCourse_notYetSoftDeleted_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.permanentlyDeleteCourse(1L, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("savatga o'tkazish");

        org.mockito.Mockito.verify(courseRepository, org.mockito.Mockito.never()).delete(org.mockito.Mockito.any());
    }

    // ===== ADMIN "Butunlay o'chirish"i — HAQIQIY o'chirmaydi, ARXIVLAYDI =====
    // Foydalanuvchi ANIQ talabi: ROLE_ADMIN o'z kursini "Butunlay o'chirish"
    // desa, kurs shu ADMIN'dan (va katalogdan) yo'qoladi, lekin bazadan
    // HAQIQIY o'chirilmaydi — ROLE_OWNER hali ham ko'ra oladi, xohlasa
    // o'z nomiga o'tkazib qayta tiklashi mumkin. ROLE_OWNER'ning o'zi
    // bosganda esa (yuqoridagi ikkita test) — o'zgarishsiz, haqiqiy o'chirish.

    @Test
    void permanentlyDeleteCourse_calledByAdmin_archivesInsteadOfHardDelete() {
        User admin = admin();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(admin)
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.permanentlyDeleteCourse(1L, admin);

        assertThat(course.getArchivedByAdmin()).isEqualTo(admin);
        assertThat(course.getArchivedAt()).isNotNull();
        // Haqiqiy (bazadan) o'chirish HECH QANDAY chaqiruvi bo'lmasligi kerak.
        org.mockito.Mockito.verify(courseRepository, org.mockito.Mockito.never()).delete(org.mockito.Mockito.any());
        org.mockito.Mockito.verifyNoInteractions(courseSectionProgressRepository, courseSubscriptionRepository,
                courseSectionRepository, courseChapterRepository);
        org.mockito.Mockito.verify(courseRepository).save(course);
    }

    @Test
    void getAdminArchivedCourses_calledByNonOwner_throwsAccessDenied() {
        assertThatThrownBy(() -> courseService.getAdminArchivedCourses(admin()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getAdminArchivedCourses_calledByOwner_returnsArchivedListWithAdminName() {
        User admin = admin();
        Course archived = Course.builder().id(1L).title("Arxivlangan kurs").createdBy(admin)
                .archivedByAdmin(admin).archivedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findArchivedByAdminOrderByArchivedAtDesc()).thenReturn(List.of(archived));

        var result = courseService.getAdminArchivedCourses(owner());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Arxivlangan kurs");
        assertThat(result.get(0).archivedByAdminName()).isEqualTo("teacher");
    }

    @Test
    void reclaimArchivedCourse_calledByOwner_transfersOwnershipAndClearsArchiveFlags() {
        User admin = admin();
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(admin).published(false)
                .archivedByAdmin(admin).archivedAt(java.time.LocalDateTime.now())
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.reclaimArchivedCourse(1L, owner);

        assertThat(course.getCreatedBy()).isEqualTo(owner);
        assertThat(course.getArchivedByAdmin()).isNull();
        assertThat(course.getArchivedAt()).isNull();
        assertThat(course.getDeletedAt()).isNull();
        // "published" ATAYLAB avtomatik yoqilmasligi kerak — OWNER buni
        // alohida o'zi yoqadi (foydalanuvchi ANIQ talabi).
        assertThat(course.isPublished()).isFalse();
    }

    @Test
    void reclaimArchivedCourse_courseNotArchived_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.reclaimArchivedCourse(1L, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arxivlangan emas");
    }

    @Test
    void reclaimArchivedCourse_calledByNonOwner_throwsAccessDenied() {
        assertThatThrownBy(() -> courseService.reclaimArchivedCourse(1L, admin()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ===== ADMIN faqat o'zi yaratgan kursni boshqara oladi =====

    private User admin() {
        return User.builder().id(2L).username("teacher").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_ADMIN").build()))).build();
    }

    // createCourse/updateCourse endi Yo'nalish (CourseField) MAJBURIY
    // talab qiladi (foydalanuvchi so'rovi, 2026-09-04) — shu testlarda
    // ishlatiladigan umumiy Yo'nalish (id=10).
    private CourseField testField() {
        return CourseField.builder().id(10L).name("Test yo'nalishi").orderIndex(1).build();
    }

    @Test
    void updateCourse_adminIsCreator_allowed() {
        User admin = admin();
        Course course = Course.builder().id(1L).title("Eski nom").createdBy(admin).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseFieldRepository.findById(10L)).thenReturn(Optional.of(testField()));

        CourseSaveDto dto = new CourseSaveDto("Yangi nom", null, null, null, null, null, 10L);
        courseService.updateCourse(1L, dto, admin);

        assertThat(course.getTitle()).isEqualTo("Yangi nom");
    }

    @Test
    void updateCourse_adminIsNotCreator_throwsAccessDenied() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSaveDto dto = new CourseSaveDto("Yangi nom", null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.updateCourse(1L, dto, admin()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("o'zingiz yaratgan");
    }

    @Test
    void deleteCourse_ownerDeletesOtherUsersCourse_allowed() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(admin()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.deleteCourse(1L, owner());

        assertThat(course.getDeletedAt()).isNotNull();
        org.mockito.Mockito.verify(courseRepository).save(course);
    }

    // ===== deleteSection (soft-delete — "O'chirilganlar savati") =====
    // Course.deletedAt bilan bir xil g'oya: progress yozuvlariga UMUMAN
    // tegilmaydi (avvalgi "Cannot delete or update a parent row" xatosi
    // bug'ini keltirib chiqargan hard-delete endi umuman ishlatilmaydi).

    @Test
    void deleteSection_softDeletes_doesNotTouchProgress() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section = CourseSection.builder().id(5L).course(course).orderIndex(1).build();
        when(courseSectionRepository.findById(5L)).thenReturn(Optional.of(section));

        courseService.deleteSection(1L, 5L, owner());

        assertThat(section.getDeletedAt()).isNotNull();
        org.mockito.Mockito.verify(courseSectionRepository).save(section);
        org.mockito.Mockito.verifyNoInteractions(courseSectionProgressRepository);
        org.mockito.Mockito.verify(courseSectionRepository, org.mockito.Mockito.never()).delete(org.mockito.Mockito.any());
    }

    @Test
    void restoreSection_clearsDeletedAt() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section = CourseSection.builder().id(5L).course(course).orderIndex(1)
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(5L)).thenReturn(Optional.of(section));

        courseService.restoreSection(1L, 5L, owner());

        assertThat(section.getDeletedAt()).isNull();
        org.mockito.Mockito.verify(courseSectionRepository).save(section);
    }

    @Test
    void permanentlyDeleteSection_softDeletedSection_deletesProgressThenSection() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section = CourseSection.builder().id(5L).course(course).orderIndex(1)
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(5L)).thenReturn(Optional.of(section));

        courseService.permanentlyDeleteSection(1L, 5L, owner());

        var inOrder = org.mockito.Mockito.inOrder(courseSectionProgressRepository, courseSectionRepository);
        inOrder.verify(courseSectionProgressRepository).deleteBySection_Id(5L);
        inOrder.verify(courseSectionRepository).delete(section);
    }

    @Test
    void permanentlyDeleteSection_notYetSoftDeleted_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section = CourseSection.builder().id(5L).course(course).orderIndex(1).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(5L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> courseService.permanentlyDeleteSection(1L, 5L, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("savatga o'tkazish");
    }

    // ===== addAllMissingTopicLinksInCourse =====
    // "➕ Butun kursda BARCHASIGA havola qo'shish" — bir nechta bog'langan
    // mavzu bo'ylab, HAR BIRIDA havolasi yo'q savollarga to'g'ri havolani
    // bir yo'lda qo'shishi kerak (haqiqiy holat: 290 mavzuli kursda
    // o'nlab yangi mavzuda havola umuman yo'q edi).

    @Test
    void addAllMissingTopicLinksInCourse_multipleSections_addsLinkToEveryQuestionMissingOne() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();

        Topic topic1 = Topic.builder().id(10L).name("1-mavzu").build();
        CourseSection section1 = CourseSection.builder().id(100L).course(course).linkedTopic(topic1).orderIndex(1).build();
        Answer a1 = Answer.builder().id(1L).answerText("Ha").isTrue(true).commentary(null).build();
        Question q1 = Question.builder().id(1L).questionText("1-savol").topic(topic1).answers(List.of(a1)).build();

        Topic topic2 = Topic.builder().id(20L).name("2-mavzu").build();
        CourseSection section2 = CourseSection.builder().id(200L).course(course).linkedTopic(topic2).orderIndex(2).build();
        // Bu savolda HAR BIR javob "isTrue=false" — findTrueAnswer null
        // qaytaradi, shu sabab O'TKAZIB YUBORILISHI kerak (xato bermasdan).
        Answer a2NoTrue = Answer.builder().id(2L).answerText("Yo'q").isTrue(false).build();
        Question q2NoTrueAnswer = Question.builder().id(2L).questionText("2-savol (to'g'ri javobsiz)").topic(topic2).answers(List.of(a2NoTrue)).build();
        // Bu savolda allaqachon TO'G'RI havola bor — TEGILMASLIGI kerak.
        Answer a3Linked = Answer.builder().id(3L).answerText("Ha").isTrue(true)
                .commentary(" <span>...<a href=\"/courses/1/sections/200\">...</a></span>").build();
        Question q3AlreadyLinked = Question.builder().id(3L).questionText("3-savol (allaqachon bog'langan)").topic(topic2).answers(List.of(a3Linked)).build();

        when(courseSectionRepository.findByCourse_IdAndLinkedTopicIsNotNull(1L)).thenReturn(List.of(section1, section2));
        when(questionRepository.getQuestionsByTopicId(10L)).thenReturn(List.of(q1));
        when(questionRepository.getQuestionsByTopicId(20L)).thenReturn(List.of(q2NoTrueAnswer, q3AlreadyLinked));

        int added = courseService.addAllMissingTopicLinksInCourse(1L);

        assertThat(added).isEqualTo(1);
        assertThat(a1.getCommentary()).contains("/courses/1/sections/100").contains("1-mavzu");
        assertThat(a3Linked.getCommentary()).isEqualTo(" <span>...<a href=\"/courses/1/sections/200\">...</a></span>");
    }

    // ===== listCatalog (kartochkadagi "N ta bo'lim, M ta mavzu") =====
    // Haqiqiy foydalanuvchi shikoyati: kartochkada FAQAT sectionCount
    // (mavzular soni) "N ta bo'lim" deb NOTO'G'RI belgilab ko'rsatilardi.
    // Endi ikkalasi ALOHIDA — chapterCount (haqiqiy CourseChapter soni)
    // va sectionCount (CourseSection soni) — chalkashmasligini tekshiradi.

    @Test
    void listCatalog_populatesBothChapterCountAndSectionCountSeparately() {
        User owner = owner();
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner).build();

        when(courseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(course));
        when(courseSectionRepository.countByCourse_Id(1L)).thenReturn(12L);
        when(courseChapterRepository.countByCourse_Id(1L)).thenReturn(3L);

        List<CourseDto> result = courseService.listCatalog(owner);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chapterCount()).isEqualTo(3);
        assertThat(result.get(0).sectionCount()).isEqualTo(12);
    }

    // ===== reorderChapters =====
    // "⬆⬇" — Bo'lim "box"larini kurs sahifasida yuqoriga/pastga surish.
    // TopicService.reorderTopics bilan bir xil andoza: frontend BUTUN
    // yangi tartibdagi id ro'yxatini yuboradi, backend orderIndex'ni
    // 1'dan qayta yozadi.

    @Test
    void reorderChapters_validIds_updatesOrderIndexInGivenOrder() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter c1 = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseChapter c2 = CourseChapter.builder().id(20L).course(course).name("2-bob").orderIndex(2).build();
        CourseChapter c3 = CourseChapter.builder().id(30L).course(course).name("3-bob").orderIndex(3).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseChapterRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(c1, c2, c3));

        courseService.reorderChapters(1L, List.of(30L, 10L, 20L), owner());

        assertThat(c3.getOrderIndex()).isEqualTo(1);
        assertThat(c1.getOrderIndex()).isEqualTo(2);
        assertThat(c2.getOrderIndex()).isEqualTo(3);
        org.mockito.Mockito.verify(courseChapterRepository).saveAll(List.of(c1, c2, c3));
    }

    @Test
    void reorderChapters_idListDoesNotMatchCourseChapters_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter c1 = CourseChapter.builder().id(10L).course(course).name("1-bob").orderIndex(1).build();
        CourseChapter c2 = CourseChapter.builder().id(20L).course(course).name("2-bob").orderIndex(2).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseChapterRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(c1, c2));

        assertThatThrownBy(() -> courseService.reorderChapters(1L, List.of(10L, 999L), owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mos kelmayapti");
    }

    // ===== deleteChapter (FAQAT bo'sh bo'lim) =====
    // Foydalanuvchi so'rovi bo'yicha tekshirilgan/tasdiqlangan himoya:
    // ichida mavzular bor Bo'limni o'chirib bo'lmaydi (avval mavzularni
    // ko'chirish/o'chirish kerak — buni ATAYLAB qiladigan yo'l esa
    // alohida, deleteChapterWithLinkedTopics).

    @Test
    void deleteChapter_hasSections_throwsAndDoesNotDelete() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(course).name("Bo'lim").orderIndex(1).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseChapterRepository.findById(10L)).thenReturn(Optional.of(chapter));
        when(courseSectionRepository.existsByChapter_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> courseService.deleteChapter(1L, 10L, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("darslar bor");

        org.mockito.Mockito.verify(courseChapterRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteChapter_empty_deletesSuccessfully() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(course).name("Bo'lim").orderIndex(1).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseChapterRepository.findById(10L)).thenReturn(Optional.of(chapter));
        when(courseSectionRepository.existsByChapter_Id(10L)).thenReturn(false);

        courseService.deleteChapter(1L, 10L, owner());

        org.mockito.Mockito.verify(courseChapterRepository).delete(chapter);
    }

    // ===== createChapter ("➕ Bo'lim qo'shish" — bo'sh bo'limni
    // to'g'ridan-to'g'ri yaratish) =====

    @Test
    void createChapter_validName_savesWithNextOrderIndexAndReturnsDto() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter existingLast = CourseChapter.builder().id(9L).course(course).name("1-bob").orderIndex(3).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseChapterRepository.findByCourse_IdAndNameIgnoreCase(1L, "2-bob")).thenReturn(Optional.empty());
        when(courseChapterRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.of(existingLast));
        when(courseChapterRepository.save(any(CourseChapter.class))).thenAnswer(inv -> {
            CourseChapter c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        CourseChapterDto result = courseService.createChapter(1L, "2-bob", owner());

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("2-bob");
        assertThat(result.orderIndex()).isEqualTo(4);
        assertThat(result.sectionCount()).isZero();
    }

    @Test
    void createChapter_blankName_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.createChapter(1L, "   ", owner()))
                .isInstanceOf(IllegalArgumentException.class);

        org.mockito.Mockito.verify(courseChapterRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createChapter_duplicateName_throwsAndDoesNotSave() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter existing = CourseChapter.builder().id(9L).course(course).name("1-bob").orderIndex(1).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseChapterRepository.findByCourse_IdAndNameIgnoreCase(1L, "1-bob")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> courseService.createChapter(1L, "1-bob", owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon mavjud");

        org.mockito.Mockito.verify(courseChapterRepository, org.mockito.Mockito.never()).save(any());
    }

    // ===== deleteChapterWithLinkedTopics =====
    // Kurs Bo'limi + mavzularini birga o'chirish — CourseSection'lar
    // soft-delete qilinadi, Bo'limning o'zi hard-delete. Foydalanuvchi
    // ANIQ talabi: TEST BOSHQARUVIdagi Topic/Question'ga HECH QACHON
    // tegilmaydi (bog'langan mavzu bo'lsa ham) — bog'lanish CourseSection
    // soft-delete qilingandan so'ng, "bog'langanmi?" so'rovlari
    // (deletedAt IS NULL filtri bilan) uni avtomatik "topmay qo'yadi".

    @Test
    void deleteChapterWithLinkedTopics_softDeletesSectionsOnly_neverTouchesTestBoshqaruvi() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseChapter chapter = CourseChapter.builder().id(10L).course(course).name("Bo'lim").orderIndex(1).build();
        TopicSection topicSection = TopicSection.builder().id(20L).name("Bo'lim").build();
        Topic topic = Topic.builder().id(30L).name("Mavzu").section(topicSection).build();
        CourseSection section = CourseSection.builder().id(5L).course(course).chapter(chapter)
                .orderIndex(1).linkedTopic(topic).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseChapterRepository.findById(10L)).thenReturn(Optional.of(chapter));
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section));

        courseService.deleteChapterWithLinkedTopics(1L, 10L, owner());

        assertThat(section.getDeletedAt()).isNotNull();
        // Haqiqiy production bug: "chapter" bog'lanishi null qilinmasa,
        // Bo'lim hard-delete qilinganda Hibernate
        // "TransientPropertyValueException: ... references an unsaved
        // transient instance" xatosini berardi.
        assertThat(section.getChapter()).isNull();
        org.mockito.Mockito.verify(courseSectionRepository).saveAll(List.of(section));
        org.mockito.Mockito.verify(courseChapterRepository).delete(chapter);
        // TEST BOSHQARUVI (Topic/Question/TopicSection) — HECH QANDAY chaqiruv bo'lmasligi kerak.
        org.mockito.Mockito.verifyNoInteractions(topicRepository, topicSectionRepository, questionRepository);
    }

    // ===== TEST BOSHQARUVI bilan bog'lash: Fan/Mavzu autocreate =====

    @Test
    void addSection_withNewScienceAndTopicNames_autocreatesAndLinksThem() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        behzoddev.testproject.entity.Science newScience =
                behzoddev.testproject.entity.Science.builder().id(10L).name("Kimyo").build();
        when(scienceRepository.findByName("Kimyo")).thenReturn(Optional.empty());
        when(scienceRepository.save(any())).thenReturn(newScience);

        behzoddev.testproject.entity.Topic newTopic =
                behzoddev.testproject.entity.Topic.builder().id(20L).name("Atom tuzilishi").science(newScience).build();
        when(topicRepository.findByScience_IdAndName(10L, "Atom tuzilishi")).thenReturn(Optional.empty());
        when(topicRepository.save(any())).thenReturn(newTopic);

        CourseSectionSaveDto dto = new CourseSectionSaveDto(
                "1-bo'lim", "TEXT", "matn", null, null, null, "Kimyo", "Atom tuzilishi", null, null, null);
        courseService.addSection(1L, dto, owner());

        var sectionCaptor = org.mockito.ArgumentCaptor.forClass(CourseSection.class);
        org.mockito.Mockito.verify(courseSectionRepository).save(sectionCaptor.capture());
        assertThat(sectionCaptor.getValue().getLinkedTopic()).isEqualTo(newTopic);
        org.mockito.Mockito.verify(scienceRepository).save(any());
        org.mockito.Mockito.verify(topicRepository).save(any());
    }

    @Test
    void addSection_withExistingScienceAndTopicNames_reusesThemWithoutCreating() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        behzoddev.testproject.entity.Science existingScience =
                behzoddev.testproject.entity.Science.builder().id(10L).name("Kimyo").build();
        when(scienceRepository.findByName("Kimyo")).thenReturn(Optional.of(existingScience));

        behzoddev.testproject.entity.Topic existingTopic =
                behzoddev.testproject.entity.Topic.builder().id(20L).name("Atom tuzilishi").science(existingScience).build();
        when(topicRepository.findByScience_IdAndName(10L, "Atom tuzilishi")).thenReturn(Optional.of(existingTopic));

        CourseSectionSaveDto dto = new CourseSectionSaveDto(
                "1-bo'lim", "TEXT", "matn", null, null, null, "Kimyo", "Atom tuzilishi", null, null, null);
        courseService.addSection(1L, dto, owner());

        org.mockito.Mockito.verify(scienceRepository, org.mockito.Mockito.never()).save(any());

        // Mavjud mavzu qayta ishlatiladi (YANGI yaratilmaydi) — lekin
        // Bo'lim holati (bu yerda — kurs mavzusi Bo'limsiz, chapter=null)
        // HAR DOIM sinxronlanadi (kurs — "haqiqiy manba"), shuning uchun
        // save() chaqiriladi (section=null qilib qo'yish uchun).
        var topicCaptor = org.mockito.ArgumentCaptor.forClass(behzoddev.testproject.entity.Topic.class);
        org.mockito.Mockito.verify(topicRepository).save(topicCaptor.capture());
        assertThat(topicCaptor.getValue().getId()).isEqualTo(20L);
        assertThat(topicCaptor.getValue().getSection()).isNull();
    }

    @Test
    void addSection_withoutScienceOrTopicName_leavesSectionUnlinked() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("1-bo'lim", "TEXT", "matn", null, null, null, null, null, null, null, null);
        courseService.addSection(1L, dto, owner());

        var sectionCaptor = org.mockito.ArgumentCaptor.forClass(CourseSection.class);
        org.mockito.Mockito.verify(courseSectionRepository).save(sectionCaptor.capture());
        assertThat(sectionCaptor.getValue().getLinkedTopic()).isNull();
        org.mockito.Mockito.verifyNoInteractions(scienceRepository, topicRepository);
    }

    // ===== reorderSections: yuqoriga/pastga ko'chirish va A-Z/Z-A saralash =====

    @Test
    void reorderSections_validIds_reassignsOrderIndexSequentially() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection s1 = CourseSection.builder().id(1L).course(course).orderIndex(1).build();
        CourseSection s2 = CourseSection.builder().id(2L).course(course).orderIndex(2).build();
        CourseSection s3 = CourseSection.builder().id(3L).course(course).orderIndex(3).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(s1, s2, s3));

        courseService.reorderSections(1L, List.of(3L, 1L, 2L), owner());

        assertThat(s3.getOrderIndex()).isEqualTo(1);
        assertThat(s1.getOrderIndex()).isEqualTo(2);
        assertThat(s2.getOrderIndex()).isEqualTo(3);
        org.mockito.Mockito.verify(courseSectionRepository).saveAll(List.of(s1, s2, s3));
    }

    @Test
    void reorderSections_idListDoesNotMatchCourseSections_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection s1 = CourseSection.builder().id(1L).course(course).orderIndex(1).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(s1));

        assertThatThrownBy(() -> courseService.reorderSections(1L, List.of(1L, 99L), owner()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reorderSections_notCreatorAdmin_throwsAccessDenied() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.reorderSections(1L, List.of(1L), admin()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== "Bepul" (free) kurs — obunasiz ham hammaga to'liq ochiq =====
    // (site'da HAM, Telegram bot'da HAM — CourseService.isSubscribed()
    // orqali umumiy mantiq).

    @Test
    void getSectionContent_freeCourse_unlockedWithoutRealSubscription() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Bepul kurs").free(true).createdBy(owner()).build();
        CourseSection section2 = CourseSection.builder().id(2L).course(course).title("2-bo'lim")
                .orderIndex(2).type(CourseSectionType.TEXT).textContent("matn").build();
        CourseSection section1 = CourseSection.builder().id(1L).course(course).orderIndex(1).build();

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findById(2L)).thenReturn(Optional.of(section2));
        // E'tibor bering: courseSubscriptionRepository haqiqiy obunani
        // TEKSHIRISH UCHUN HECH QACHON chaqirilmaydi — free=true bo'lgani
        // uchun isSubscribed() qisqa yo'l bilan true qaytaradi.
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 1)).thenReturn(Optional.of(section1));
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(true);
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 2L)).thenReturn(false);
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 3)).thenReturn(Optional.empty());
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(section1, section2));

        CourseSectionContentDto result = courseService.getSectionContent(1L, 2L, user);

        assertThat(result.title()).isEqualTo("2-bo'lim");
        org.mockito.Mockito.verifyNoInteractions(courseSubscriptionRepository);
    }

    @Test
    void getDetail_freeCourse_subscribedTrueWithoutRealSubscription() {
        User user = subscriber();
        Course course = Course.builder().id(1L).title("Bepul kurs").published(true).free(true).createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        // E'tibor bering: existsByUser_IdAndCourse_IdAndStatus (PENDING so'rov
        // bormi tekshiruvi) HECH QACHON chaqirilmaydi — subscribed=true
        // (free=true tufayli) bo'lgani uchun requestPending'ning "&&" qisqa
        // yo'l bilan to'xtaydi, shuning uchun bu yerda stub kerak emas.
        when(courseSectionRepository.findByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

        CourseDetailDto result = courseService.getDetail(1L, user);

        assertThat(result.free()).isTrue();
        assertThat(result.subscribed()).isTrue();
        org.mockito.Mockito.verify(courseSubscriptionRepository, org.mockito.Mockito.never())
                .existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(any(), any(), any(), any());
    }

    @Test
    void createCourse_freeTrue_setsFreeFlag() {
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseFieldRepository.findById(10L)).thenReturn(Optional.of(testField()));

        CourseSaveDto dto = new CourseSaveDto("Kurs", null, null, null, true, null, 10L);
        CourseDto result = courseService.createCourse(dto, owner());

        assertThat(result.free()).isTrue();
    }

    @Test
    void updateCourse_freeNull_leavesExistingFreeUnchanged() {
        Course course = Course.builder().id(1L).title("Kurs").free(true).createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseFieldRepository.findById(10L)).thenReturn(Optional.of(testField()));

        CourseSaveDto dto = new CourseSaveDto("Yangi nom", null, null, null, null, null, 10L);
        courseService.updateCourse(1L, dto, owner());

        assertThat(course.isFree()).isTrue();
    }

    // ===== Pullik kurs narxi (price) =====

    @Test
    void createCourse_withPrice_savesPrice() {
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseFieldRepository.findById(10L)).thenReturn(Optional.of(testField()));

        CourseSaveDto dto = new CourseSaveDto("Kurs", null, null, null, false,
                new java.math.BigDecimal("150000"), 10L);
        CourseDto result = courseService.createCourse(dto, owner());

        assertThat(result.price()).isEqualByComparingTo("150000");
    }

    @Test
    void updateCourse_withNewPrice_overwritesExistingPrice() {
        Course course = Course.builder().id(1L).title("Kurs")
                .price(new java.math.BigDecimal("100000")).createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseFieldRepository.findById(10L)).thenReturn(Optional.of(testField()));

        CourseSaveDto dto = new CourseSaveDto("Kurs", null, null, null, null,
                new java.math.BigDecimal("200000"), 10L);
        courseService.updateCourse(1L, dto, owner());

        assertThat(course.getPrice()).isEqualByComparingTo("200000");
    }

    // ===== dedupeTopicLinksInCourse — 2026-08-31 haqiqiy production bug =====
    // TOPIC_LINK_BADGE_PATTERN avval "<span[^>]*>\s*<a" edi — \s* faqol
    // bo'sh joyni tutadi, lekin haqiqiy belgida <span> bilan <a> orasida
    // "📖 " (emoji) bor edi, shu sabab replaceAll() faqat <a>...</a></span>
    // qismini olib tashlab, "<span ...>📖 " qismini "yetim" holda
    // qoldirardi. Bir necha marta takrorlangach — buzuq, ko'p marta
    // ochilgan-lekin-yopilmagan <span> qatlamlari hosil bo'lgan edi
    // (Kimyo kursida 270+ ta savolda). Bu test aynan o'sha buzuq holatni
    // qayta tiklab, tuzatilgan regex uni to'liq (bitta toza belgigacha)
    // tozalashini tasdiqlaydi.
    @Test
    void dedupeTopicLinksInCourse_corruptedOrphanedSpans_fullyRepaired() {
        Course course = Course.builder().id(2L).title("Kimyo").build();
        Topic topic = Topic.builder().id(3L).name("2. Prokariotlarning tasnifi").build();
        CourseSection section = CourseSection.builder().id(2L).course(course).linkedTopic(topic).build();

        String corrupted = "Kislorod elementi haqida. " +
                "<span style=\"display:inline-block;margin-top:6px;padding:4px 10px 4px 8px;background:#e8f5f3;border-left:3px solid #00796b;border-radius:4px;color:#00695c;font-weight:600\">📖  " +
                "<span style=\"display:inline-block;margin-top:6px;padding:4px 10px 4px 8px;background:#e8f5f3;border-left:3px solid #00796b;border-radius:4px;color:#00695c;font-weight:600;font-style:normal;text-decoration:none\">📖  " +
                "<span style=\"display:inline-block;margin-top:6px;padding:4px 10px 4px 8px;background:#e8f5f3;border-left:3px solid #00796b;border-radius:4px;color:#00695c;font-weight:600;font-style:normal;text-decoration:none\">📖 " +
                "<a href=\"/courses/2/sections/2\" style=\"color:#00695c;text-decoration:underline\">\"1. Modda, atom, molekula\" mavzusini kursda o'qish</a></span>";

        Answer trueAnswer = Answer.builder().id(1L).answerText("To'g'ri").isTrue(true).commentary(corrupted).build();
        Answer wrongAnswer = Answer.builder().id(2L).answerText("Noto'g'ri").isTrue(false).build();
        Question question = Question.builder().id(100L).questionText("Savol")
                .answers(List.of(trueAnswer, wrongAnswer)).build();

        when(courseSectionRepository.findByCourse_IdAndLinkedTopicIsNotNull(2L)).thenReturn(List.of(section));
        when(questionRepository.getQuestionsByTopicId(3L)).thenReturn(List.of(question));

        int fixed = courseService.dedupeTopicLinksInCourse(2L);

        assertThat(fixed).isEqualTo(1);
        String result = trueAnswer.getCommentary();

        long spanOpen = result.split("<span", -1).length - 1;
        long spanClose = result.split("</span>", -1).length - 1;
        assertThat(spanOpen).as("ochilgan <span> soni yopilgan bilan teng bo'lishi kerak").isEqualTo(spanClose);
        assertThat(spanOpen).as("aynan BITTA toza belgi qolishi kerak").isEqualTo(1);

        long hrefCount = result.split("href=", -1).length - 1;
        assertThat(hrefCount).isEqualTo(1);
        assertThat(result).contains("href=\"/courses/2/sections/2\"");
        assertThat(result).contains("Kislorod elementi haqida.");
        assertThat(result).doesNotContain("📖  <span");
    }
}
