package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.topic.TopicNameDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.TopicMapper;
import behzoddev.testproject.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicMapper topicMapper;
    @Mock
    private ScienceRepository scienceRepository;
    @Mock
    private TopicSectionRepository topicSectionRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private AnswerService answerService;

    private TopicService topicService;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(answerService);
        topicService = new TopicService(topicRepository, topicMapper, scienceRepository, topicSectionRepository, courseSectionRepository, validation);
    }

    @Test
    void saveTopic_success_linksQuestionsAndScience() {
        Question q = Question.builder().id(1L).questionText("Q1").build();
        Topic mapped = Topic.builder().id(1L).name("Mavzu").questions(java.util.Set.of(q)).build();
        Science science = Science.builder().id(5L).name("Matematika").build();

        when(topicMapper.mapTopicNameDtoToTopic(any())).thenReturn(mapped);
        when(scienceRepository.findById(5L)).thenReturn(Optional.of(science));
        when(topicRepository.save(mapped)).thenReturn(mapped);

        Topic result = topicService.saveTopic(5L, new TopicNameDto("Mavzu"));

        assertThat(result.getScience()).isEqualTo(science);
        assertThat(q.getTopic()).isEqualTo(mapped);
    }

    @Test
    void saveTopic_scienceNotFound_stillSavesWithNullScience() {
        Topic mapped = Topic.builder().id(1L).name("Mavzu").build();
        when(topicMapper.mapTopicNameDtoToTopic(any())).thenReturn(mapped);
        when(scienceRepository.findById(999L)).thenReturn(Optional.empty());
        when(topicRepository.save(mapped)).thenReturn(mapped);

        Topic result = topicService.saveTopic(999L, new TopicNameDto("Mavzu"));

        assertThat(result.getScience()).isNull();
    }

    @Test
    void saveTopic_blankName_throwsBeforeMapping() {
        assertThatThrownBy(() -> topicService.saveTopic(1L, new TopicNameDto(" ")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(topicMapper, never()).mapTopicNameDtoToTopic(any());
    }

    @Test
    void updateTopic_blankName_throwsBeforeUpdating() {
        assertThatThrownBy(() -> topicService.updateTopic(1L, ""))
                .isInstanceOf(IllegalArgumentException.class);

        verify(topicRepository, never()).updateTopicName(any(), any());
    }

    @Test
    void updateTopic_validName_delegatesToRepository() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(Topic.builder().id(1L).name("Eski nom").build()));

        topicService.updateTopic(1L, "Yangi nom");

        verify(topicRepository).updateTopicName(1L, "Yangi nom");
    }

    // ===== Kursga bog'langan mavzuni TEST BOSHQARUVIDAN tahrirlashni bloklash =====

    @Test
    void updateTopic_linkedToCourseAndNameChanges_throwsAndDoesNotUpdate() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(Topic.builder().id(1L).name("Eski nom").build()));
        behzoddev.testproject.entity.Course course = behzoddev.testproject.entity.Course.builder().title("Kimyo asoslari").build();
        behzoddev.testproject.entity.CourseSection section = behzoddev.testproject.entity.CourseSection.builder().course(course).build();
        when(courseSectionRepository.findByLinkedTopic_Id(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> topicService.updateTopic(1L, "Yangi nom"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kimyo asoslari");

        verify(topicRepository, never()).updateTopicName(any(), any());
    }

    // Faqat mavzuning Bo'limi (sectionId) o'zgartirilganda ham
    // TopicController.saveTopic har doim updateTopic'ni (nom O'ZGARMAGAN
    // holda) chaqiradi — bu holatda bloklanmasligi kerak (aks holda kursga
    // bog'langan mavzuning Bo'limini qayta biriktirish ham imkonsiz
    // bo'lib qolar edi, bu so'ralmagan).
    @Test
    void updateTopic_linkedToCourseButNameUnchanged_updatesSuccessfully() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(Topic.builder().id(1L).name("Bir xil nom").build()));

        topicService.updateTopic(1L, "Bir xil nom");

        verify(topicRepository).updateTopicName(1L, "Bir xil nom");
        verify(courseSectionRepository, never()).findByLinkedTopic_Id(any());
    }

    @Test
    void removeTopic_delegatesToRepository() {
        topicService.removeTopic(1L);

        verify(topicRepository).deleteById(1L);
    }
}
