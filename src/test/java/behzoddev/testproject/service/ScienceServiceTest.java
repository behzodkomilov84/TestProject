package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.science.ScienceNameDto;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.ScienceMapper;
import behzoddev.testproject.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScienceServiceTest {

    @Mock
    private ScienceRepository scienceRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private CourseFieldRepository courseFieldRepository;
    @Mock
    private ScienceMapper scienceMapper;
    @Mock
    private AnswerService answerService;

    private ScienceService scienceService;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(answerService);
        scienceService = new ScienceService(scienceRepository, topicRepository, courseFieldRepository, scienceMapper, validation);
    }

    @Test
    void saveScience_success_linksTopicsBackToScience() {
        Topic topic = Topic.builder().id(1L).name("Mavzu").build();
        Science mapped = Science.builder().id(1L).name("Matematika").topics(Set.of(topic)).build();

        when(scienceRepository.existsByName("Matematika")).thenReturn(false);
        when(scienceMapper.mapScienceNameDtoToScience(any())).thenReturn(mapped);
        when(scienceRepository.save(mapped)).thenReturn(mapped);

        Science result = scienceService.saveScience(new ScienceNameDto("Matematika"));

        assertThat(result.getName()).isEqualTo("Matematika");
        assertThat(topic.getScience()).isEqualTo(mapped);
    }

    @Test
    void saveScience_blankName_throwsBeforeCheckingDuplicate() {
        assertThatThrownBy(() -> scienceService.saveScience(new ScienceNameDto("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bo'sh bo'lishi mumkin emas");

        verify(scienceRepository, org.mockito.Mockito.never()).existsByName(any());
    }

    @Test
    void saveScience_duplicateName_throws() {
        when(scienceRepository.existsByName("Matematika")).thenReturn(true);

        assertThatThrownBy(() -> scienceService.saveScience(new ScienceNameDto("Matematika")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allaqachon mavjud");

        verify(scienceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void isScienceNameExist_present_returnsTrue() {
        when(scienceRepository.findByName("Fizika")).thenReturn(Optional.of(Science.builder().id(1L).name("Fizika").build()));

        assertThat(scienceService.isScienceNameExist("Fizika")).isTrue();
    }

    @Test
    void isScienceNameExist_absent_returnsFalse() {
        when(scienceRepository.findByName("Kimyo")).thenReturn(Optional.empty());

        assertThat(scienceService.isScienceNameExist("Kimyo")).isFalse();
    }

    @Test
    void saveScience_withFieldId_linksToField() {
        Science mapped = Science.builder().id(1L).name("Kimyo").build();
        CourseField field = CourseField.builder().id(9L).name("O'rta ta'lim").build();

        when(scienceRepository.existsByName("Kimyo")).thenReturn(false);
        when(scienceMapper.mapScienceNameDtoToScience(any())).thenReturn(mapped);
        when(courseFieldRepository.findById(9L)).thenReturn(Optional.of(field));
        when(scienceRepository.save(mapped)).thenReturn(mapped);

        Science result = scienceService.saveScience(new ScienceNameDto("Kimyo", 9L));

        assertThat(result.getField()).isEqualTo(field);
    }

    @Test
    void saveScience_fieldIdNotFound_throws() {
        Science mapped = Science.builder().id(1L).name("Kimyo").build();
        when(scienceRepository.existsByName("Kimyo")).thenReturn(false);
        when(scienceMapper.mapScienceNameDtoToScience(any())).thenReturn(mapped);
        when(courseFieldRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scienceService.saveScience(new ScienceNameDto("Kimyo", 9L)))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(scienceRepository, org.mockito.Mockito.never()).save(any());
    }

    // ===== assignField ("🔀 Yo'nalishga biriktirish") =====

    @Test
    void assignField_validField_setsField() {
        Science science = Science.builder().id(1L).name("Kimyo").build();
        CourseField field = CourseField.builder().id(9L).name("O'rta ta'lim").build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));
        when(courseFieldRepository.findById(9L)).thenReturn(Optional.of(field));

        scienceService.assignField(1L, 9L);

        assertThat(science.getField()).isEqualTo(field);
        verify(scienceRepository).save(science);
    }

    @Test
    void assignField_nullFieldId_unlinks() {
        CourseField field = CourseField.builder().id(9L).name("O'rta ta'lim").build();
        Science science = Science.builder().id(1L).name("Kimyo").field(field).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.assignField(1L, null);

        assertThat(science.getField()).isNull();
        verify(scienceRepository).save(science);
    }

    @Test
    void updateScienceName_blank_throwsBeforeUpdating() {
        assertThatThrownBy(() -> scienceService.updateScienceName(1L, " "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(scienceRepository, org.mockito.Mockito.never()).updateScienceName(any(), any());
    }

    @Test
    void getSciences_mapsEntitiesToIdAndNameDtos() {
        when(scienceRepository.findAll()).thenReturn(List.of(
                Science.builder().id(1L).name("Matematika").build(),
                Science.builder().id(2L).name("Fizika").build()));

        List<ScienceIdAndNameDto> result = scienceService.getSciences();

        assertThat(result).containsExactly(
                new ScienceIdAndNameDto(1L, "Matematika"),
                new ScienceIdAndNameDto(2L, "Fizika"));
    }

    // ===== removeScience (soft-delete — "O'chirilganlar savati") =====

    @Test
    void removeScience_softDeletes_doesNotHardDelete() {
        Science science = Science.builder().id(1L).name("Kimyo").build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.removeScience(1L);

        assertThat(science.getDeletedAt()).isNotNull();
        verify(scienceRepository).save(science);
        verify(scienceRepository, org.mockito.Mockito.never()).deleteById(any());
        verify(scienceRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void removeScience_notFound_throws() {
        when(scienceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scienceService.removeScience(1L))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ===== restoreScience =====

    @Test
    void restoreScience_clearsDeletedAt() {
        Science science = Science.builder().id(1L).name("Kimyo")
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.restoreScience(1L);

        assertThat(science.getDeletedAt()).isNull();
        verify(scienceRepository).save(science);
    }

    @Test
    void restoreScience_notDeleted_throws() {
        Science science = Science.builder().id(1L).name("Kimyo").build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        assertThatThrownBy(() -> scienceService.restoreScience(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o'chirilmagan");
    }

    // ===== permanentlyDeleteScience =====

    @Test
    void permanentlyDeleteScience_softDeletedScience_hardDeletes() {
        Science science = Science.builder().id(1L).name("Kimyo")
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.permanentlyDeleteScience(1L);

        verify(scienceRepository).delete(science);
    }

    @Test
    void permanentlyDeleteScience_notYetSoftDeleted_throws() {
        Science science = Science.builder().id(1L).name("Kimyo").build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        assertThatThrownBy(() -> scienceService.permanentlyDeleteScience(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("savatga o'tkazish");
    }
}
