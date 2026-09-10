package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.UserActivityTotalRepository;
import behzoddev.testproject.dao.UserCourseActivityTotalRepository;
import behzoddev.testproject.dto.user.UserActivitySummaryDto;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.UserActivityTotal;
import behzoddev.testproject.entity.UserCourseActivityTotal;
import behzoddev.testproject.entity.compositeKey.UserCourseActivityKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * "/users" sahifasidagi "Oxirgi tashrif vaqti" oynasi uchun — DB'dagi
 * to'plangan qiymatlarni kurs nomlari bilan birlashtirib beradi.
 */
@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    @Mock
    private UserActivityTotalRepository userActivityTotalRepository;
    @Mock
    private UserCourseActivityTotalRepository userCourseActivityTotalRepository;
    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private UserActivityService userActivityService;

    @Test
    void getSummary_noActivityYet_returnsZeroAndEmptyBreakdown() {
        when(userActivityTotalRepository.findById(1L)).thenReturn(Optional.empty());
        when(userCourseActivityTotalRepository.findById_UserIdOrderByTotalSecondsDesc(1L)).thenReturn(List.of());

        UserActivitySummaryDto result = userActivityService.getSummary(1L);

        assertThat(result.totalSeconds()).isZero();
        assertThat(result.courseBreakdown()).isEmpty();
    }

    @Test
    void getSummary_withActivity_combinesTotalAndCourseBreakdownWithTitles() {
        when(userActivityTotalRepository.findById(1L))
                .thenReturn(Optional.of(UserActivityTotal.builder().userId(1L).totalSeconds(7200).build()));

        UserCourseActivityTotal row1 = UserCourseActivityTotal.builder()
                .id(new UserCourseActivityKey(1L, 5L)).totalSeconds(3600).build();
        UserCourseActivityTotal row2 = UserCourseActivityTotal.builder()
                .id(new UserCourseActivityKey(1L, 9L)).totalSeconds(1200).build();
        when(userCourseActivityTotalRepository.findById_UserIdOrderByTotalSecondsDesc(1L))
                .thenReturn(List.of(row1, row2));

        Course course5 = Course.builder().id(5L).title("Bakteriologiya").build();
        when(courseRepository.findAllById(List.of(5L, 9L))).thenReturn(List.of(course5));

        UserActivitySummaryDto result = userActivityService.getSummary(1L);

        assertThat(result.totalSeconds()).isEqualTo(7200);
        assertThat(result.courseBreakdown()).hasSize(2);
        assertThat(result.courseBreakdown().get(0).courseId()).isEqualTo(5L);
        assertThat(result.courseBreakdown().get(0).courseTitle()).isEqualTo("Bakteriologiya");
        assertThat(result.courseBreakdown().get(0).totalSeconds()).isEqualTo(3600);
        // Kurs 9 CourseRepository'da topilmagan (masalan o'chirilgan) —
        // baribir nom bilan (zaxira matn) ko'rinishi kerak, tarix yo'qolmasin.
        assertThat(result.courseBreakdown().get(1).courseId()).isEqualTo(9L);
        assertThat(result.courseBreakdown().get(1).courseTitle()).isEqualTo("(o'chirilgan kurs)");
    }
}
