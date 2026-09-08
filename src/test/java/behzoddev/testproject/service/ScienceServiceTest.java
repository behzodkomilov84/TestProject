package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dao.TopicSectionRepository;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.science.ScienceNameDto;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.ScienceMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScienceServiceTest {

    @Mock
    private ScienceRepository scienceRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicSectionRepository topicSectionRepository;
    @Mock
    private CourseFieldRepository courseFieldRepository;
    @Mock
    private ScienceMapper scienceMapper;
    @Mock
    private AnswerService answerService;

    private ScienceService scienceService;

    private User owner;
    private User admin;
    private User otherAdmin;

    @BeforeEach
    void setUp() {
        Validation validation = new Validation(answerService);
        scienceService = new ScienceService(scienceRepository, topicRepository, topicSectionRepository,
                courseFieldRepository, scienceMapper, validation);

        owner = User.builder().id(99L).username("owner").roles(new HashSet<>(Set.of(
                Role.builder().id(1L).roleName("ROLE_OWNER").build()))).build();
        admin = User.builder().id(50L).username("admin1").roles(new HashSet<>(Set.of(
                Role.builder().id(2L).roleName("ROLE_ADMIN").build()))).build();
        otherAdmin = User.builder().id(51L).username("admin2").roles(new HashSet<>(Set.of(
                Role.builder().id(3L).roleName("ROLE_ADMIN").build()))).build();
    }

    @Test
    void saveScience_success_linksTopicsBackToScienceAndStampsCreator() {
        Topic topic = Topic.builder().id(1L).name("Mavzu").build();
        Science mapped = Science.builder().id(1L).name("Matematika").topics(Set.of(topic)).build();

        when(scienceRepository.existsByName("Matematika")).thenReturn(false);
        when(scienceMapper.mapScienceNameDtoToScience(any())).thenReturn(mapped);
        when(scienceRepository.save(mapped)).thenReturn(mapped);

        Science result = scienceService.saveScience(new ScienceNameDto("Matematika"), admin);

        assertThat(result.getName()).isEqualTo("Matematika");
        assertThat(topic.getScience()).isEqualTo(mapped);
        assertThat(mapped.getCreatedBy()).isEqualTo(admin);
    }

    @Test
    void saveScience_blankName_throwsBeforeCheckingDuplicate() {
        assertThatThrownBy(() -> scienceService.saveScience(new ScienceNameDto("  "), admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bo'sh bo'lishi mumkin emas");

        verify(scienceRepository, org.mockito.Mockito.never()).existsByName(any());
    }

    @Test
    void saveScience_duplicateName_throws() {
        when(scienceRepository.existsByName("Matematika")).thenReturn(true);

        assertThatThrownBy(() -> scienceService.saveScience(new ScienceNameDto("Matematika"), admin))
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

        Science result = scienceService.saveScience(new ScienceNameDto("Kimyo", 9L), admin);

        assertThat(result.getField()).isEqualTo(field);
    }

    @Test
    void saveScience_fieldIdNotFound_throws() {
        Science mapped = Science.builder().id(1L).name("Kimyo").build();
        when(scienceRepository.existsByName("Kimyo")).thenReturn(false);
        when(scienceMapper.mapScienceNameDtoToScience(any())).thenReturn(mapped);
        when(courseFieldRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scienceService.saveScience(new ScienceNameDto("Kimyo", 9L), admin))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(scienceRepository, org.mockito.Mockito.never()).save(any());
    }

    // ===== assignField ("🔀 Yo'nalishga biriktirish") =====

    @Test
    void assignField_validField_setsField() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        CourseField field = CourseField.builder().id(9L).name("O'rta ta'lim").build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));
        when(courseFieldRepository.findById(9L)).thenReturn(Optional.of(field));

        scienceService.assignField(1L, 9L, admin);

        assertThat(science.getField()).isEqualTo(field);
        verify(scienceRepository).save(science);
    }

    @Test
    void assignField_nullFieldId_unlinks() {
        CourseField field = CourseField.builder().id(9L).name("O'rta ta'lim").build();
        Science science = Science.builder().id(1L).name("Kimyo").field(field).createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.assignField(1L, null, admin);

        assertThat(science.getField()).isNull();
        verify(scienceRepository).save(science);
    }

    @Test
    void assignField_byUnrelatedAdmin_throwsAccessDenied() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        assertThatThrownBy(() -> scienceService.assignField(1L, 9L, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(scienceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateScienceName_blank_throwsBeforeUpdating() {
        assertThatThrownBy(() -> scienceService.updateScienceName(1L, " ", admin))
                .isInstanceOf(IllegalArgumentException.class);

        verify(scienceRepository, org.mockito.Mockito.never()).updateScienceName(any(), any());
    }

    // ADMIN o'zi yaratgan fanni boshqara oladi; OWNER cheklovsiz; boshqa
    // ADMIN esa bloklanadi (foydalanuvchi so'rovi, 2026-09-08: "ROLE_ADMIN
    // o'zi yaratmagan hech qaysi joyda o'zgartirish qila olmasin").
    @Test
    void updateScienceName_byCreatorAdmin_allowed() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.updateScienceName(1L, "Yangi nom", admin);

        verify(scienceRepository).updateScienceName(1L, "Yangi nom");
    }

    @Test
    void updateScienceName_byOwner_alwaysAllowed() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.updateScienceName(1L, "Yangi nom", owner);

        verify(scienceRepository).updateScienceName(1L, "Yangi nom");
    }

    @Test
    void updateScienceName_byUnrelatedAdmin_throwsAccessDenied() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        assertThatThrownBy(() -> scienceService.updateScienceName(1L, "Yangi nom", otherAdmin))
                .isInstanceOf(AccessDeniedException.class);

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
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.removeScience(1L, admin);

        assertThat(science.getDeletedAt()).isNotNull();
        verify(scienceRepository).save(science);
        verify(scienceRepository, org.mockito.Mockito.never()).deleteById(any());
        verify(scienceRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void removeScience_notFound_throws() {
        when(scienceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scienceService.removeScience(1L, admin))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void removeScience_byUnrelatedAdmin_throwsAccessDenied() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        assertThatThrownBy(() -> scienceService.removeScience(1L, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(scienceRepository, org.mockito.Mockito.never()).save(any());
    }

    // ===== restoreScience =====

    @Test
    void restoreScience_clearsDeletedAt() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin)
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.restoreScience(1L, admin);

        assertThat(science.getDeletedAt()).isNull();
        verify(scienceRepository).save(science);
    }

    @Test
    void restoreScience_notDeleted_throws() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        assertThatThrownBy(() -> scienceService.restoreScience(1L, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o'chirilmagan");
    }

    // ===== permanentlyDeleteScience =====

    @Test
    void permanentlyDeleteScience_softDeletedScience_hardDeletes() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin)
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        scienceService.permanentlyDeleteScience(1L, admin);

        verify(scienceRepository).delete(science);
    }

    @Test
    void permanentlyDeleteScience_notYetSoftDeleted_throws() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        assertThatThrownBy(() -> scienceService.permanentlyDeleteScience(1L, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("savatga o'tkazish");
    }

    // ===== checkCanManageByTopicId / checkCanManageBySectionId =====
    // (ExcelImportController kabi topicId/sectionId'ga tayanadigan
    // joylar uchun qulaylik metodlari.)

    @Test
    void checkCanManageByTopicId_ownerOfTopicsScience_doesNotThrow() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        Topic topic = Topic.builder().id(5L).science(science).build();
        when(topicRepository.findById(5L)).thenReturn(Optional.of(topic));

        scienceService.checkCanManageByTopicId(5L, admin);
        // no exception — pass
    }

    @Test
    void checkCanManageByTopicId_unrelatedAdmin_throws() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        Topic topic = Topic.builder().id(5L).science(science).build();
        when(topicRepository.findById(5L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> scienceService.checkCanManageByTopicId(5L, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checkCanManageBySectionId_unrelatedAdmin_throws() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        behzoddev.testproject.entity.TopicSection section =
                behzoddev.testproject.entity.TopicSection.builder().id(7L).science(science).build();
        when(topicSectionRepository.findById(7L)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> scienceService.checkCanManageBySectionId(7L, otherAdmin))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== canManageByTopicId (otmaydigan/non-throwing variant) =====

    @Test
    void canManageByTopicId_ownerOfTopicsScience_returnsTrue() {
        Science science = Science.builder().id(1L).createdBy(admin).build();
        Topic topic = Topic.builder().id(3L).science(science).build();
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic));

        assertThat(scienceService.canManageByTopicId(3L, admin)).isTrue();
    }

    @Test
    void canManageByTopicId_unrelatedAdmin_returnsFalse() {
        Science science = Science.builder().id(1L).createdBy(admin).build();
        Topic topic = Topic.builder().id(3L).science(science).build();
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic));

        assertThat(scienceService.canManageByTopicId(3L, otherAdmin)).isFalse();
    }

    @Test
    void canManageByTopicId_topicNotFound_returnsFalse() {
        when(topicRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(scienceService.canManageByTopicId(999L, admin)).isFalse();
    }

    // ===== getAllScienceIdAndNameDto(User) — FRONTEND'da tugmalarni
    // ko'rsatish/yashirish uchun canManage bilan boyitilgan ro'yxat
    // (foydalanuvchi so'rovi, 2026-09-08: "FRONTEND da ham modify
    // qilolmasin"). =====

    @Test
    void getAllScienceIdAndNameDto_marksOwnedAndUnownedSciencesCorrectly() {
        Science owned = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        Science notOwned = Science.builder().id(2L).name("Fizika").createdBy(otherAdmin).build();
        when(scienceRepository.findAllScienceNames()).thenReturn(Set.of(
                new ScienceIdAndNameDto(1L, "Kimyo", 0),
                new ScienceIdAndNameDto(2L, "Fizika", 0)
        ));
        when(scienceRepository.findAllByDeletedAtIsNullOrderByOrderIndex())
                .thenReturn(List.of(owned, notOwned));

        Set<ScienceIdAndNameDto> result = scienceService.getAllScienceIdAndNameDto(admin);

        assertThat(result).extracting(ScienceIdAndNameDto::id, ScienceIdAndNameDto::canManage)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, true),
                        org.assertj.core.groups.Tuple.tuple(2L, false)
                );
    }

    @Test
    void getAllScienceIdAndNameDto_owner_alwaysCanManage() {
        Science notOwned = Science.builder().id(2L).name("Fizika").createdBy(admin).build();
        when(scienceRepository.findAllScienceNames()).thenReturn(Set.of(new ScienceIdAndNameDto(2L, "Fizika", 0)));
        when(scienceRepository.findAllByDeletedAtIsNullOrderByOrderIndex()).thenReturn(List.of(notOwned));

        Set<ScienceIdAndNameDto> result = scienceService.getAllScienceIdAndNameDto(owner);

        assertThat(result).extracting(ScienceIdAndNameDto::canManage).containsExactly(true);
    }

    // ===== getScienceNameById(Long, User) =====

    @Test
    void getScienceNameById_unrelatedAdmin_canManageFalse() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findScienceNameById(1L)).thenReturn(Optional.of(new ScienceIdAndNameDto(1L, "Kimyo", 0)));
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        Optional<ScienceIdAndNameDto> result = scienceService.getScienceNameById(1L, otherAdmin);

        assertThat(result).isPresent();
        assertThat(result.get().canManage()).isFalse();
    }

    @Test
    void getScienceNameById_creatingAdmin_canManageTrue() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findScienceNameById(1L)).thenReturn(Optional.of(new ScienceIdAndNameDto(1L, "Kimyo", 0)));
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        Optional<ScienceIdAndNameDto> result = scienceService.getScienceNameById(1L, admin);

        assertThat(result).isPresent();
        assertThat(result.get().canManage()).isTrue();
    }
}
