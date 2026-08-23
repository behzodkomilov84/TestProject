package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseChapterRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.course.CourseDetailDto;
import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.course.CourseSaveDto;
import behzoddev.testproject.dto.course.CourseSectionContentDto;
import behzoddev.testproject.dto.course.CourseSectionSaveDto;
import behzoddev.testproject.dto.course.CourseSectionSummaryDto;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseSection;
import behzoddev.testproject.entity.Role;
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
        when(courseSectionRepository.findByCourse_IdAndOrderIndex(1L, 1)).thenReturn(Optional.of(section1));
        when(courseSectionProgressRepository.existsByUser_IdAndSection_Id(1L, 1L)).thenReturn(false);

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

        CourseSectionSaveDto dto = new CourseSectionSaveDto(" ", "TEXT", "matn", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mavzu nomi bo'sh");
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
        CourseSectionSaveDto dto = new CourseSectionSaveDto(tooLongTitle, "TEXT", "matn", null, null, null, null, null, null, null);

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
        CourseSectionSaveDto dto = new CourseSectionSaveDto(maxTitle, "TEXT", "matn", null, null, null, null, null, null, null);

        CourseSectionSummaryDto result = courseService.addSection(1L, dto, owner());

        assertThat(result.title()).isEqualTo(maxTitle);
    }

    @Test
    void addSection_textTypeWithoutTextContent_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("Sarlavha", "TEXT", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto, owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Matn kontenti bo'sh");
    }

    @Test
    void addSection_videoTypeWithoutUrl_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("Sarlavha", "VIDEO", null, "YOUTUBE", null, null, null, null, null, null);

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

        CourseSectionSaveDto dto = new CourseSectionSaveDto("3-bo'lim", "TEXT", "matn", null, null, null, null, null, null, null);
        CourseSectionSummaryDto result = courseService.addSection(1L, dto, owner());

        assertThat(result.orderIndex()).isEqualTo(3);
    }

    @Test
    void addSection_firstSectionInCourse_orderIndexOne() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("1-bo'lim", "TEXT", "matn", null, null, null, null, null, null, null);
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

    // ===== deleteCourse =====
    // Haqiqiy production bug: course_sections/course_subscriptions FK
    // RESTRICT bo'lgani uchun (ON DELETE CASCADE emas), bog'liq yozuvlar
    // avval o'chirilmasa, kursning o'zini o'chirish "Cannot delete or
    // update a parent row: a foreign key constraint fails" xatosi bilan
    // muvaffaqiyatsiz tugardi.

    @Test
    void deleteCourse_deletesDependentRecordsBeforeCourseItself() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.deleteCourse(1L, owner());

        var inOrder = org.mockito.Mockito.inOrder(
                courseSectionProgressRepository, courseSubscriptionRepository,
                courseSectionRepository, courseRepository);
        inOrder.verify(courseSectionProgressRepository).deleteBySection_Course_Id(1L);
        inOrder.verify(courseSubscriptionRepository).deleteByCourse_Id(1L);
        inOrder.verify(courseSectionRepository).deleteByCourse_Id(1L);
        inOrder.verify(courseRepository).delete(course);
    }

    @Test
    void deleteCourse_courseNotFound_throws() {
        when(courseRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deleteCourse(1L, owner()))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ===== ADMIN faqat o'zi yaratgan kursni boshqara oladi =====

    private User admin() {
        return User.builder().id(2L).username("teacher").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_ADMIN").build()))).build();
    }

    @Test
    void updateCourse_adminIsCreator_allowed() {
        User admin = admin();
        Course course = Course.builder().id(1L).title("Eski nom").createdBy(admin).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSaveDto dto = new CourseSaveDto("Yangi nom", null, null, null, null, null);
        courseService.updateCourse(1L, dto, admin);

        assertThat(course.getTitle()).isEqualTo("Yangi nom");
    }

    @Test
    void updateCourse_adminIsNotCreator_throwsAccessDenied() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSaveDto dto = new CourseSaveDto("Yangi nom", null, null, null, null, null);

        assertThatThrownBy(() -> courseService.updateCourse(1L, dto, admin()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("o'zingiz yaratgan");
    }

    @Test
    void deleteCourse_ownerDeletesOtherUsersCourse_allowed() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(admin()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.deleteCourse(1L, owner());

        org.mockito.Mockito.verify(courseRepository).delete(course);
    }

    // ===== deleteSection: bo'limga tegishli progress yozuvlari avval o'chishi kerak =====
    // Haqiqiy production bug: course_section_progress.section_id FK RESTRICT
    // bo'lgani uchun, foydalanuvchi bo'limni "tugatilgan" deb belgilagan
    // bo'lsa, bo'limni o'chirish "Bu amalni bajarib bo'lmadi — bog'liq
    // ma'lumotlar mavjud" (409) xatosi bilan muvaffaqiyatsiz tugardi.

    @Test
    void deleteSection_deletesProgressBeforeSectionItself() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        CourseSection section = CourseSection.builder().id(5L).course(course).orderIndex(1).build();
        when(courseSectionRepository.findById(5L)).thenReturn(Optional.of(section));

        courseService.deleteSection(1L, 5L, owner());

        var inOrder = org.mockito.Mockito.inOrder(courseSectionProgressRepository, courseSectionRepository);
        inOrder.verify(courseSectionProgressRepository).deleteBySection_Id(5L);
        inOrder.verify(courseSectionRepository).delete(section);
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
                "1-bo'lim", "TEXT", "matn", null, null, null, "Kimyo", "Atom tuzilishi", null, null);
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
                "1-bo'lim", "TEXT", "matn", null, null, null, "Kimyo", "Atom tuzilishi", null, null);
        courseService.addSection(1L, dto, owner());

        org.mockito.Mockito.verify(scienceRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(topicRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void addSection_withoutScienceOrTopicName_leavesSectionUnlinked() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("1-bo'lim", "TEXT", "matn", null, null, null, null, null, null, null);
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

        CourseSaveDto dto = new CourseSaveDto("Kurs", null, null, null, true, null);
        CourseDto result = courseService.createCourse(dto, owner());

        assertThat(result.free()).isTrue();
    }

    @Test
    void updateCourse_freeNull_leavesExistingFreeUnchanged() {
        Course course = Course.builder().id(1L).title("Kurs").free(true).createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSaveDto dto = new CourseSaveDto("Yangi nom", null, null, null, null, null);
        courseService.updateCourse(1L, dto, owner());

        assertThat(course.isFree()).isTrue();
    }

    // ===== Pullik kurs narxi (price) =====

    @Test
    void createCourse_withPrice_savesPrice() {
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseSaveDto dto = new CourseSaveDto("Kurs", null, null, null, false,
                new java.math.BigDecimal("150000"));
        CourseDto result = courseService.createCourse(dto, owner());

        assertThat(result.price()).isEqualByComparingTo("150000");
    }

    @Test
    void updateCourse_withNewPrice_overwritesExistingPrice() {
        Course course = Course.builder().id(1L).title("Kurs")
                .price(new java.math.BigDecimal("100000")).createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSaveDto dto = new CourseSaveDto("Kurs", null, null, null, null,
                new java.math.BigDecimal("200000"));
        courseService.updateCourse(1L, dto, owner());

        assertThat(course.getPrice()).isEqualByComparingTo("200000");
    }
}
