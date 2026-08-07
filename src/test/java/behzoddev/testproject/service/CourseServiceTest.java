package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dto.course.CourseDetailDto;
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
    private CourseSubscriptionRepository courseSubscriptionRepository;
    @Mock
    private CourseSectionProgressRepository courseSectionProgressRepository;

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

        CourseSectionSaveDto dto = new CourseSectionSaveDto(" ", "TEXT", "matn", null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bo'lim nomi bo'sh");
    }

    @Test
    void addSection_textTypeWithoutTextContent_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("Sarlavha", "TEXT", null, null, null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Matn kontenti bo'sh");
    }

    @Test
    void addSection_videoTypeWithoutUrl_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("Sarlavha", "VIDEO", null, "YOUTUBE", null, null);

        assertThatThrownBy(() -> courseService.addSection(1L, dto))
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

        CourseSectionSaveDto dto = new CourseSectionSaveDto("3-bo'lim", "TEXT", "matn", null, null, null);
        CourseSectionSummaryDto result = courseService.addSection(1L, dto);

        assertThat(result.orderIndex()).isEqualTo(3);
    }

    @Test
    void addSection_firstSectionInCourse_orderIndexOne() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSectionRepository.findTopByCourse_IdOrderByOrderIndexDesc(1L)).thenReturn(Optional.empty());
        when(courseSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseSectionSaveDto dto = new CourseSectionSaveDto("1-bo'lim", "TEXT", "matn", null, null, null);
        CourseSectionSummaryDto result = courseService.addSection(1L, dto);

        assertThat(result.orderIndex()).isEqualTo(1);
    }

    // ===== deleteSection: bo'lim boshqa kursga tegishli bo'lsa =====

    @Test
    void deleteSection_sectionBelongsToDifferentCourse_throws() {
        Course course = Course.builder().id(1L).title("Kurs").createdBy(owner()).build();
        Course otherCourse = Course.builder().id(2L).title("Boshqa kurs").createdBy(owner()).build();
        CourseSection section = CourseSection.builder().id(1L).course(otherCourse).orderIndex(1).build();

        when(courseSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> courseService.deleteSection(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bu kursga tegishli emas");
    }
}
