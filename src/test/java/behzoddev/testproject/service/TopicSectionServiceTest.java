package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.mapper.TopicSectionMapper;
import behzoddev.testproject.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicSectionServiceTest {

    @Mock
    private TopicSectionRepository topicSectionRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private ScienceRepository scienceRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private TopicSectionMapper topicSectionMapper;
    @Mock
    private AnswerService answerService;

    private TopicSectionService topicSectionService;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(answerService);
        topicSectionService = new TopicSectionService(
                topicSectionRepository, topicRepository, scienceRepository, courseSectionRepository,
                topicSectionMapper, validation);
    }

    // ===== removeSection (soft-delete — "O'chirilganlar savati") =====

    @Test
    void removeSection_softDeletes_doesNotHardDelete() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim").build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        topicSectionService.removeSection(1L);

        assertThat(section.getDeletedAt()).isNotNull();
        verify(topicSectionRepository).save(section);
        verify(topicSectionRepository, never()).deleteById(any());
        verify(topicSectionRepository, never()).delete(any());
    }

    @Test
    void removeSection_notFound_throws() {
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicSectionService.removeSection(1L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ===== restoreSection =====

    @Test
    void restoreSection_clearsDeletedAt() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim")
                .deletedAt(LocalDateTime.now()).build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        topicSectionService.restoreSection(1L);

        assertThat(section.getDeletedAt()).isNull();
        verify(topicSectionRepository).save(section);
    }

    @Test
    void restoreSection_notDeleted_throws() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim").build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> topicSectionService.restoreSection(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o'chirilmagan");
    }

    // ===== permanentlyDeleteSection =====

    @Test
    void permanentlyDeleteSection_softDeletedSection_hardDeletes() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim")
                .deletedAt(LocalDateTime.now()).build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        topicSectionService.permanentlyDeleteSection(1L);

        verify(topicSectionRepository).delete(section);
    }

    @Test
    void permanentlyDeleteSection_notYetSoftDeleted_throws() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim").build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> topicSectionService.permanentlyDeleteSection(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("savatga o'tkazish");
    }
}
