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

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "Test
    // boshqaruvida o'chirib bo'lmayapti" — "Bu amalni bajarib bo'lmadi —
    // bog'liq ma'lumotlar mavjud" xatosi bilan, ekran surati bilan) —
    // "topics.science_id" FK'si "NO ACTION"/RESTRICT bo'lgani uchun
    // fanida BIRON BIR mavzu (hatto savatga o'tkazilgan bo'lsa ham)
    // qolib ketsa, oddiy "scienceRepository.delete()" DOIM shu umumiy,
    // tushunarsiz xatoga uchrardi. Foydalanuvchi ATAYLAB avtomatik
    // ommaviy o'chirishni (kaskad) BEKOR qildi — xavfsizroq: o'rniga
    // ANIQ, sonli xabar (frontend shu asosda "Ko'rish" tugmasini
    // ko'rsatadi — getTopicsBlockingDeletion() orqali).
    @Test
    void permanentlyDeleteScience_withRemainingTopics_throwsWithCount() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin)
                .deletedAt(java.time.LocalDateTime.now()).build();
        Topic t1 = Topic.builder().id(10L).name("1-mavzu").science(science).build();
        Topic t2 = Topic.builder().id(20L).name("2-mavzu").science(science)
                .deletedAt(java.time.LocalDateTime.now()).build();

        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));
        when(topicRepository.findByScience_Id(1L)).thenReturn(List.of(t1, t2));

        assertThatThrownBy(() -> scienceService.permanentlyDeleteScience(1L, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2");

        verify(scienceRepository, org.mockito.Mockito.never()).delete(any());
    }

    // "Ko'rish" tugmasi (foydalanuvchi so'rovi, 2026-09-12) — fanni
    // o'chirishga to'sqinlik qilayotgan mavzular ro'yxatini (faol VA
    // savatdagi — ikkalasi ham) qaytarishini tasdiqlaydi.
    @Test
    void getTopicsBlockingDeletion_returnsAllTopicsRegardlessOfDeletedState() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin)
                .deletedAt(java.time.LocalDateTime.now()).build();
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));
        List<behzoddev.testproject.dto.topic.TopicTrashDto> expected = List.of(
                new behzoddev.testproject.dto.topic.TopicTrashDto(10L, "1-mavzu", null, 3L, null, null));
        when(topicRepository.findAllByScienceIdIncludingDeleted(1L)).thenReturn(expected);

        List<behzoddev.testproject.dto.topic.TopicTrashDto> result =
                scienceService.getTopicsBlockingDeletion(1L, admin);

        assertThat(result).isEqualTo(expected);
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
    void getAllScienceIdAndNameDto_adminSeesOnlyOwnScience_othersFilteredOut() {
        // Foydalanuvchi so'rovi, 2026-09-08: "OWNER dan tashqari hamma
        // adminlar faqat o'zi yaratgan testlar iyerarxiyasini ko'ra
        // olsin. Boshqalarniki ko'rinmasin" — shu sabab notOwned ro'yxatda
        // UMUMAN chiqmasligi kerak (canManage=false bilan belgilanib
        // qolish emas).
        Science owned = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        Science notOwned = Science.builder().id(2L).name("Fizika").createdBy(otherAdmin).build();
        when(scienceRepository.findAllScienceBasics()).thenReturn(List.of(
                new ScienceIdAndNameDto(1L, "Kimyo", 0),
                new ScienceIdAndNameDto(2L, "Fizika", 0)
        ));
        when(scienceRepository.findAllByDeletedAtIsNullOrderByOrderIndex())
                .thenReturn(List.of(owned, notOwned));

        Set<ScienceIdAndNameDto> result = scienceService.getAllScienceIdAndNameDto(admin);

        assertThat(result).extracting(ScienceIdAndNameDto::id, ScienceIdAndNameDto::canManage)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, true));
    }

    @Test
    void getAllScienceIdAndNameDto_owner_alwaysCanManage() {
        Science notOwned = Science.builder().id(2L).name("Fizika").createdBy(admin).build();
        when(scienceRepository.findAllScienceBasics()).thenReturn(List.of(new ScienceIdAndNameDto(2L, "Fizika", 0)));
        when(scienceRepository.findAllByDeletedAtIsNullOrderByOrderIndex()).thenReturn(List.of(notOwned));

        Set<ScienceIdAndNameDto> result = scienceService.getAllScienceIdAndNameDto(owner);

        assertThat(result).extracting(ScienceIdAndNameDto::canManage).containsExactly(true);
    }

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "TEST
    // BOSHQARUVI dagi barcha N+1 so'rov muammolarini ko'rib chiq") —
    // sectionCount endi ALOHIDA, BULK (GROUP BY) so'rov bilan to'g'ri
    // birlashtirilishini tasdiqlaydi (korrelyatsiyalangan subso'rov o'rniga).
    @Test
    void getAllScienceIdAndNameDto_mergesGroupedSectionCounts() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(owner).build();
        when(scienceRepository.findAllScienceBasics())
                .thenReturn(List.of(new ScienceIdAndNameDto(1L, "Kimyo", 0)));
        when(scienceRepository.findAllByDeletedAtIsNullOrderByOrderIndex()).thenReturn(List.of(science));
        when(topicSectionRepository.countByScienceIdsGrouped(List.of(1L)))
                .thenReturn(List.of(new behzoddev.testproject.dto.science.ScienceSectionCountDto(1L, 7L)));

        Set<ScienceIdAndNameDto> result = scienceService.getAllScienceIdAndNameDto(owner);

        assertThat(result).extracting(ScienceIdAndNameDto::sectionCount).containsExactly(7L);
    }

    // ===== getScienceNameById(Long, User) =====

    @Test
    void getScienceNameById_unrelatedAdmin_returnsEmpty_notVisible() {
        // Foydalanuvchi so'rovi, 2026-09-08: "Boshqalarniki ko'rinmasin" —
        // shu sabab canManage=false bilan qaytarish o'rniga endi UMUMAN
        // ko'rinmaydi (Optional.empty() — 404).
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findScienceNameById(1L)).thenReturn(Optional.of(new ScienceIdAndNameDto(1L, "Kimyo", 0)));
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(science));

        Optional<ScienceIdAndNameDto> result = scienceService.getScienceNameById(1L, otherAdmin);

        assertThat(result).isEmpty();
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

    // ===== getScienceById(Long, User) — /science/{id}/full =====

    @Test
    void getScienceById_unrelatedAdmin_returnsEmpty() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        when(scienceRepository.findByIdWithTopics(1L)).thenReturn(Optional.of(science));

        assertThat(scienceService.getScienceById(1L, otherAdmin)).isEmpty();
    }

    @Test
    void getScienceById_creatingAdmin_returnsMapped() {
        Science science = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        behzoddev.testproject.dto.science.ScienceDto mapped =
                new behzoddev.testproject.dto.science.ScienceDto(1L, "Kimyo", Set.of());
        when(scienceRepository.findByIdWithTopics(1L)).thenReturn(Optional.of(science));
        when(scienceMapper.mapSciencetoScienceDto(science)).thenReturn(mapped);

        assertThat(scienceService.getScienceById(1L, admin)).contains(mapped);
    }

    // ===== getAllSciencesDto(User) — /science/full =====

    @Test
    void getAllSciencesDto_adminSeesOnlyOwnScience() {
        Science owned = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        Science notOwned = Science.builder().id(2L).name("Fizika").createdBy(otherAdmin).build();
        when(scienceRepository.findAllWithTopics()).thenReturn(new HashSet<>(Set.of(owned, notOwned)));

        scienceService.getAllSciencesDto(admin);

        org.mockito.ArgumentCaptor<Set<Science>> captor = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(scienceMapper).toScinceDtoSet(captor.capture());
        assertThat(captor.getValue()).containsExactly(owned);
    }

    // ===== getDeletedSciences(User) — "O'chirilganlar savati" =====

    @Test
    void getDeletedSciences_adminSeesOnlyOwnDeletedScience() {
        Science owned = Science.builder().id(1L).name("Kimyo").createdBy(admin).build();
        Science notOwned = Science.builder().id(2L).name("Fizika").createdBy(otherAdmin).build();
        when(scienceRepository.findAllDeleted()).thenReturn(List.of(
                new behzoddev.testproject.dto.science.ScienceTrashDto(1L, "Kimyo", null),
                new behzoddev.testproject.dto.science.ScienceTrashDto(2L, "Fizika", null)
        ));
        when(scienceRepository.findById(1L)).thenReturn(Optional.of(owned));
        when(scienceRepository.findById(2L)).thenReturn(Optional.of(notOwned));

        List<behzoddev.testproject.dto.science.ScienceTrashDto> result = scienceService.getDeletedSciences(admin);

        assertThat(result).extracting(behzoddev.testproject.dto.science.ScienceTrashDto::id).containsExactly(1L);
    }

    @Test
    void getDeletedSciences_owner_seesAll() {
        when(scienceRepository.findAllDeleted()).thenReturn(List.of(
                new behzoddev.testproject.dto.science.ScienceTrashDto(1L, "Kimyo", null),
                new behzoddev.testproject.dto.science.ScienceTrashDto(2L, "Fizika", null)
        ));

        List<behzoddev.testproject.dto.science.ScienceTrashDto> result = scienceService.getDeletedSciences(owner);

        assertThat(result).hasSize(2);
    }
}
