package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.question.TopicQuestionCountDto;
import behzoddev.testproject.dto.topic.TopicCourseTitleDto;
import behzoddev.testproject.dto.topic.TopicIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicNameDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.TopicMapper;
import behzoddev.testproject.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
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
class TopicServiceTest {

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private TopicMapper topicMapper;
    @Mock
    private TopicSectionRepository topicSectionRepository;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private AnswerService answerService;
    @Mock
    private ScienceService scienceService;

    private TopicService topicService;
    private User admin;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(answerService);
        topicService = new TopicService(topicRepository, questionRepository, topicMapper,
                topicSectionRepository, courseSectionRepository, validation, scienceService);

        admin = User.builder().id(50L).username("admin1").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_ADMIN").build()))).build();
    }

    @Test
    void saveTopic_success_linksQuestionsAndScience() {
        Question q = Question.builder().id(1L).questionText("Q1").build();
        Topic mapped = Topic.builder().id(1L).name("Mavzu").questions(java.util.Set.of(q)).build();
        Science science = Science.builder().id(5L).name("Matematika").createdBy(admin).build();

        when(topicMapper.mapTopicNameDtoToTopic(any())).thenReturn(mapped);
        when(scienceService.requireManageableScience(5L, admin)).thenReturn(science);
        when(topicRepository.save(mapped)).thenReturn(mapped);

        Topic result = topicService.saveTopic(5L, new TopicNameDto("Mavzu"), admin);

        assertThat(result.getScience()).isEqualTo(science);
        assertThat(q.getTopic()).isEqualTo(mapped);
    }

    // ADMIN o'zi yaratmagan Fanga mavzu qo'sha olmaydi — requireManageableScience
    // AccessDeniedException tashlaydi (haqiqiy tekshiruv ScienceServiceTest'da,
    // bu yerda faqat bog'lanish/wiring tekshiriladi).
    @Test
    void saveTopic_unmanageableScience_propagatesAccessDenied() {
        when(scienceService.requireManageableScience(5L, admin))
                .thenThrow(new AccessDeniedException("⛔ Faqat o'zingiz yaratgan fanni boshqarishingiz mumkin."));

        assertThatThrownBy(() -> topicService.saveTopic(5L, new TopicNameDto("Mavzu"), admin))
                .isInstanceOf(AccessDeniedException.class);

        verify(topicRepository, never()).save(any());
    }

    @Test
    void saveTopic_blankName_throwsBeforeMapping() {
        assertThatThrownBy(() -> topicService.saveTopic(1L, new TopicNameDto(" "), admin))
                .isInstanceOf(IllegalArgumentException.class);

        verify(topicMapper, never()).mapTopicNameDtoToTopic(any());
    }

    @Test
    void updateTopic_blankName_throwsBeforeUpdating() {
        assertThatThrownBy(() -> topicService.updateTopic(1L, "", admin))
                .isInstanceOf(IllegalArgumentException.class);

        verify(topicRepository, never()).updateTopicName(any(), any());
    }

    @Test
    void updateTopic_validName_delegatesToRepository() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(Topic.builder().id(1L).name("Eski nom").build()));

        topicService.updateTopic(1L, "Yangi nom", admin);

        verify(topicRepository).updateTopicName(1L, "Yangi nom");
    }

    @Test
    void updateTopic_unmanageableScience_throwsAndDoesNotUpdate() {
        Science science = Science.builder().id(5L).build();
        when(topicRepository.findById(1L)).thenReturn(Optional.of(Topic.builder().id(1L).name("Eski nom").science(science).build()));
        doThrow(new AccessDeniedException("⛔")).when(scienceService).checkCanManage(science, admin);

        assertThatThrownBy(() -> topicService.updateTopic(1L, "Yangi nom", admin))
                .isInstanceOf(AccessDeniedException.class);

        verify(topicRepository, never()).updateTopicName(any(), any());
    }

    // ===== Kursga bog'langan mavzuni TEST BOSHQARUVIDAN tahrirlashni bloklash =====

    @Test
    void updateTopic_linkedToCourseAndNameChanges_throwsAndDoesNotUpdate() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(Topic.builder().id(1L).name("Eski nom").build()));
        behzoddev.testproject.entity.Course course = behzoddev.testproject.entity.Course.builder().title("Kimyo asoslari").build();
        behzoddev.testproject.entity.CourseSection section = behzoddev.testproject.entity.CourseSection.builder().course(course).build();
        when(courseSectionRepository.findByLinkedTopic_Id(1L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> topicService.updateTopic(1L, "Yangi nom", admin))
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

        topicService.updateTopic(1L, "Bir xil nom", admin);

        verify(topicRepository).updateTopicName(1L, "Bir xil nom");
        verify(courseSectionRepository, never()).findByLinkedTopic_Id(any());
    }

    // ===== removeTopic (soft-delete — "O'chirilganlar savati") =====

    @Test
    void removeTopic_softDeletes_doesNotTouchQuestions() {
        Topic topic = Topic.builder().id(1L).name("Mavzu").build();
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        topicService.removeTopic(1L, admin);

        assertThat(topic.getDeletedAt()).isNotNull();
        verify(topicRepository).save(topic);
        verify(topicRepository, never()).deleteById(any());
        verify(questionRepository, never()).deleteByTopic_Id(any());
    }

    @Test
    void removeTopic_notFound_throws() {
        when(topicRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.removeTopic(1L, admin))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void removeTopic_unmanageableScience_throws() {
        Science science = Science.builder().id(5L).build();
        Topic topic = Topic.builder().id(1L).name("Mavzu").science(science).build();
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        doThrow(new AccessDeniedException("⛔")).when(scienceService).checkCanManage(science, admin);

        assertThatThrownBy(() -> topicService.removeTopic(1L, admin))
                .isInstanceOf(AccessDeniedException.class);

        verify(topicRepository, never()).save(any());
    }

    // ===== restoreTopic =====

    @Test
    void restoreTopic_clearsDeletedAt() {
        Topic topic = Topic.builder().id(1L).name("Mavzu")
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        topicService.restoreTopic(1L, admin);

        assertThat(topic.getDeletedAt()).isNull();
        verify(topicRepository).save(topic);
    }

    @Test
    void restoreTopic_notDeleted_throws() {
        Topic topic = Topic.builder().id(1L).name("Mavzu").build();
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> topicService.restoreTopic(1L, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o'chirilmagan");
    }

    // ===== permanentlyDeleteTopic =====

    @Test
    void permanentlyDeleteTopic_softDeletedTopic_deletesQuestionsThenTopic() {
        Topic topic = Topic.builder().id(1L).name("Mavzu")
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        topicService.permanentlyDeleteTopic(1L, admin);

        verify(questionRepository).deleteByTopic_Id(1L);
        verify(topicRepository).delete(topic);
    }

    @Test
    void permanentlyDeleteTopic_notYetSoftDeleted_throws() {
        Topic topic = Topic.builder().id(1L).name("Mavzu").build();
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> topicService.permanentlyDeleteTopic(1L, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("savatga o'tkazish");

        verify(topicRepository, never()).delete(any());
    }

    // ===== getTopicsByScienceId (HAQIQIY TOPILGAN BUG, 2026-09-12:
    // "/topics sahifa juda sekin yuklanyapti") — endi HAR BIR mavzu uchun
    // korrelyatsiyalangan subso'rov o'rniga, bulk (GROUP BY) so'rovlar
    // bilan birlashtiriladi. =====

    @Test
    void getTopicsByScienceId_mergesGroupedCountsAndCourseTitles_notPerTopicSubqueries() {
        TopicIdAndNameDto t1 = new TopicIdAndNameDto(1L, "Mavzu 1", 10L);
        TopicIdAndNameDto t2 = new TopicIdAndNameDto(2L, "Mavzu 2", 10L);
        when(topicRepository.findTopicBasicsByScienceId(5L)).thenReturn(List.of(t1, t2));
        when(questionRepository.countByTopicIdsGrouped(List.of(1L, 2L)))
                .thenReturn(List.of(new TopicQuestionCountDto(1L, 3L)));
        when(questionRepository.countDeletedByTopicIdsGrouped(List.of(1L, 2L)))
                .thenReturn(List.of(new TopicQuestionCountDto(2L, 1L)));
        when(courseSectionRepository.findLinkedCourseTitlesByScienceId(5L))
                .thenReturn(List.of(new TopicCourseTitleDto(1L, "Bakteriologiya")));

        List<TopicIdAndNameDto> result = topicService.getTopicsByScienceId(5L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).questionCount()).isEqualTo(3);
        assertThat(result.get(0).trashedQuestionCount()).isEqualTo(0);
        assertThat(result.get(0).linkedCourseTitle()).isEqualTo("Bakteriologiya");
        assertThat(result.get(1).questionCount()).isEqualTo(0);
        assertThat(result.get(1).trashedQuestionCount()).isEqualTo(1);
        assertThat(result.get(1).linkedCourseTitle()).isNull();
    }

    @Test
    void getTopicsByScienceId_noTopics_returnsEmptyWithoutFurtherQueries() {
        when(topicRepository.findTopicBasicsByScienceId(5L)).thenReturn(List.of());

        List<TopicIdAndNameDto> result = topicService.getTopicsByScienceId(5L);

        assertThat(result).isEmpty();
        verify(questionRepository, never()).countByTopicIdsGrouped(any());
    }

    // ===== deleteQuestionlessTopics — bulk (GROUP BY) so'rovga
    // o'tkazilgandan keyin ham to'g'ri ishlashini tasdiqlaydi. =====

    @Test
    void deleteQuestionlessTopics_deletesOnlyUnlinkedTopicsWithNoActiveQuestions() {
        TopicIdAndNameDto empty = new TopicIdAndNameDto(1L, "Bo'sh mavzu", null);
        TopicIdAndNameDto withQuestions = new TopicIdAndNameDto(2L, "Savolli mavzu", null);
        TopicIdAndNameDto linkedEmpty = new TopicIdAndNameDto(3L, "Kursga bog'langan bo'sh mavzu", null);
        when(topicRepository.findTopicBasicsByScienceId(5L)).thenReturn(List.of(empty, withQuestions, linkedEmpty));
        when(questionRepository.countByTopicIdsGrouped(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(new TopicQuestionCountDto(2L, 5L)));
        when(courseSectionRepository.findLinkedCourseTitlesByScienceId(5L))
                .thenReturn(List.of(new TopicCourseTitleDto(3L, "Bakteriologiya")));
        Topic emptyTopic = Topic.builder().id(1L).name("Bo'sh mavzu").build();
        when(topicRepository.findAllById(List.of(1L))).thenReturn(List.of(emptyTopic));

        int deleted = topicService.deleteQuestionlessTopics(5L, admin);

        assertThat(deleted).isEqualTo(1);
        assertThat(emptyTopic.getDeletedAt()).isNotNull();
        verify(topicRepository).saveAll(List.of(emptyTopic));
    }
}
