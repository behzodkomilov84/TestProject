package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.TopicSection;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.TopicSectionMapper;
import behzoddev.testproject.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private TopicSectionMapper topicSectionMapper;
    @Mock
    private AnswerService answerService;
    @Mock
    private ScienceService scienceService;

    private TopicSectionService topicSectionService;
    private User admin;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(answerService);
        topicSectionService = new TopicSectionService(
                topicSectionRepository, topicRepository, courseSectionRepository,
                topicSectionMapper, validation, scienceService);

        admin = User.builder().id(50L).username("admin1").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_ADMIN").build()))).build();
    }

    // ===== removeSection (soft-delete — "O'chirilganlar savati") =====

    @Test
    void removeSection_softDeletes_doesNotHardDelete() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim").build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        topicSectionService.removeSection(1L, admin);

        assertThat(section.getDeletedAt()).isNotNull();
        verify(topicSectionRepository).save(section);
        verify(topicSectionRepository, never()).deleteById(any());
        verify(topicSectionRepository, never()).delete(any());
    }

    @Test
    void removeSection_notFound_throws() {
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicSectionService.removeSection(1L, admin))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removeSection_unmanageableScience_throws() {
        Science science = Science.builder().id(5L).build();
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim").science(science).build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));
        doThrow(new AccessDeniedException("⛔")).when(scienceService).checkCanManage(science, admin);

        assertThatThrownBy(() -> topicSectionService.removeSection(1L, admin))
                .isInstanceOf(AccessDeniedException.class);

        verify(topicSectionRepository, never()).save(any());
    }

    // ===== restoreSection =====

    @Test
    void restoreSection_clearsDeletedAt() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim")
                .deletedAt(LocalDateTime.now()).build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        topicSectionService.restoreSection(1L, admin);

        assertThat(section.getDeletedAt()).isNull();
        verify(topicSectionRepository).save(section);
    }

    @Test
    void restoreSection_notDeleted_throws() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim").build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> topicSectionService.restoreSection(1L, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o'chirilmagan");
    }

    // ===== permanentlyDeleteSection =====

    @Test
    void permanentlyDeleteSection_softDeletedSection_hardDeletes() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim")
                .deletedAt(LocalDateTime.now()).build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        topicSectionService.permanentlyDeleteSection(1L, admin);

        verify(topicSectionRepository).delete(section);
    }

    @Test
    void permanentlyDeleteSection_notYetSoftDeleted_throws() {
        TopicSection section = TopicSection.builder().id(1L).name("Bo'lim").build();
        when(topicSectionRepository.findById(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> topicSectionService.permanentlyDeleteSection(1L, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("savatga o'tkazish");
    }
}
