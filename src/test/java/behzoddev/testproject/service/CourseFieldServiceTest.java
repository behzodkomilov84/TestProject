package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dto.course.CourseFieldDto;
import behzoddev.testproject.dto.course.CourseFieldSaveDto;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * "Yo'nalish" (CourseField) — Kurslar (Bo'limlar) katalogini kattaroq
 * guruhga bo'lish. Asosiy e'tibor: yaratish/nomini o'zgartirish, faqat
 * BO'SH Yo'nalishni o'chirish ruxsati, va tartiblash.
 */
@ExtendWith(MockitoExtension.class)
class CourseFieldServiceTest {

    @Mock
    private CourseFieldRepository courseFieldRepository;
    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseFieldService courseFieldService;

    private User owner() {
        return User.builder().id(1L).username("owner").roles(new HashSet<>(Set.of(
                Role.builder().id(1L).roleName("ROLE_OWNER").build()))).build();
    }

    @Test
    void createField_blankName_throws() {
        assertThatThrownBy(() -> courseFieldService.createField(new CourseFieldSaveDto(" "), owner()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nomi bo'sh");
    }

    @Test
    void createField_firstField_getsOrderIndexOne() {
        when(courseFieldRepository.findTopByOrderIndexDesc()).thenReturn(Optional.empty());
        when(courseFieldRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            CourseField f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
        when(courseRepository.countByField_IdAndDeletedAtIsNull(1L)).thenReturn(0L);

        CourseFieldDto result = courseFieldService.createField(new CourseFieldSaveDto("Yangi soha"), owner());

        assertThat(result.orderIndex()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("Yangi soha");
        assertThat(result.courseCount()).isZero();
    }

    @Test
    void createField_appendsAfterExisting() {
        CourseField last = CourseField.builder().id(5L).name("Oxirgi").orderIndex(3).build();
        when(courseFieldRepository.findTopByOrderIndexDesc()).thenReturn(Optional.of(last));
        when(courseFieldRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            CourseField f = inv.getArgument(0);
            f.setId(6L);
            return f;
        });
        when(courseRepository.countByField_IdAndDeletedAtIsNull(6L)).thenReturn(0L);

        CourseFieldDto result = courseFieldService.createField(new CourseFieldSaveDto("Yangi soha"), owner());

        assertThat(result.orderIndex()).isEqualTo(4);
    }

    @Test
    void renameField_blankName_throws() {
        assertThatThrownBy(() -> courseFieldService.renameField(1L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nomi bo'sh");
    }

    @Test
    void deleteField_hasActiveCourses_throwsAndDoesNotDelete() {
        CourseField field = CourseField.builder().id(1L).name("Soha").orderIndex(1).build();
        when(courseFieldRepository.findById(1L)).thenReturn(Optional.of(field));
        when(courseFieldRepository.existsActiveCourseByField_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> courseFieldService.deleteField(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kurslar");

        org.mockito.Mockito.verify(courseFieldRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteField_empty_softDeletes() {
        CourseField field = CourseField.builder().id(1L).name("Soha").orderIndex(1).build();
        when(courseFieldRepository.findById(1L)).thenReturn(Optional.of(field));
        when(courseFieldRepository.existsActiveCourseByField_Id(1L)).thenReturn(false);

        courseFieldService.deleteField(1L);

        assertThat(field.getDeletedAt()).isNotNull();
        org.mockito.Mockito.verify(courseFieldRepository).save(field);
    }

    @Test
    void restoreField_notDeleted_throws() {
        CourseField field = CourseField.builder().id(1L).name("Soha").orderIndex(1).build();
        when(courseFieldRepository.findById(1L)).thenReturn(Optional.of(field));

        assertThatThrownBy(() -> courseFieldService.restoreField(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o'chirilmagan");
    }

    @Test
    void reorderFields_validIds_reassignsOrderIndexSequentially() {
        CourseField f1 = CourseField.builder().id(1L).name("A").orderIndex(1).build();
        CourseField f2 = CourseField.builder().id(2L).name("B").orderIndex(2).build();
        CourseField f3 = CourseField.builder().id(3L).name("C").orderIndex(3).build();
        when(courseFieldRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(f1, f2, f3));

        courseFieldService.reorderFields(List.of(3L, 1L, 2L));

        assertThat(f3.getOrderIndex()).isEqualTo(1);
        assertThat(f1.getOrderIndex()).isEqualTo(2);
        assertThat(f2.getOrderIndex()).isEqualTo(3);
        org.mockito.Mockito.verify(courseFieldRepository).saveAll(List.of(f1, f2, f3));
    }

    @Test
    void reorderFields_idListDoesNotMatch_throws() {
        CourseField f1 = CourseField.builder().id(1L).name("A").orderIndex(1).build();
        when(courseFieldRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(f1));

        assertThatThrownBy(() -> courseFieldService.reorderFields(List.of(1L, 99L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listFields_mapsCourseCountPerField() {
        CourseField f1 = CourseField.builder().id(1L).name("A").orderIndex(1).build();
        when(courseFieldRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(f1));
        when(courseRepository.countByField_IdAndDeletedAtIsNull(1L)).thenReturn(3L);

        List<CourseFieldDto> result = courseFieldService.listFields();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseCount()).isEqualTo(3);
    }
}
