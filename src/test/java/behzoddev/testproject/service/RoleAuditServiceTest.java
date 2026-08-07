package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleAuditLogRepository;
import behzoddev.testproject.dto.audit.RoleAuditLogDto;
import behzoddev.testproject.entity.RoleAuditLog;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.RoleAuditAction;
import behzoddev.testproject.entity.enums.RoleAuditSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAuditServiceTest {

    @Mock
    private RoleAuditLogRepository roleAuditLogRepository;

    @InjectMocks
    private RoleAuditService roleAuditService;

    @Test
    void record_savesLogWithGivenFields() {
        User target = User.builder().id(1L).username("bob").build();
        User admin = User.builder().id(2L).username("owner").build();

        roleAuditService.record(target, admin, "ROLE_ADMIN", RoleAuditAction.GRANTED, RoleAuditSource.MANUAL);

        ArgumentCaptor<RoleAuditLog> captor = ArgumentCaptor.forClass(RoleAuditLog.class);
        verify(roleAuditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetUser()).isEqualTo(target);
        assertThat(captor.getValue().getChangedBy()).isEqualTo(admin);
        assertThat(captor.getValue().getRoleName()).isEqualTo("ROLE_ADMIN");
        assertThat(captor.getValue().getAction()).isEqualTo(RoleAuditAction.GRANTED);
        assertThat(captor.getValue().getSource()).isEqualTo(RoleAuditSource.MANUAL);
    }

    @Test
    void record_systemChange_allowsNullChangedBy() {
        User target = User.builder().id(1L).username("bob").build();

        roleAuditService.record(target, null, "ROLE_ADMIN", RoleAuditAction.REVOKED, RoleAuditSource.SYSTEM);

        ArgumentCaptor<RoleAuditLog> captor = ArgumentCaptor.forClass(RoleAuditLog.class);
        verify(roleAuditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isNull();
    }

    @Test
    void listRecent_mapsAndFallsBackToSystemLabelWhenChangedByNull() {
        User target = User.builder().id(1L).username("bob").build();
        RoleAuditLog log = RoleAuditLog.builder().id(1L).targetUser(target).changedBy(null)
                .roleName("ROLE_ADMIN").action(RoleAuditAction.REVOKED).source(RoleAuditSource.SYSTEM).build();
        when(roleAuditLogRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        List<RoleAuditLogDto> result = roleAuditService.listRecent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).changedById()).isNull();
        assertThat(result.get(0).changedByUsername()).isEqualTo("Tizim (avtomatik)");
        assertThat(result.get(0).targetUsername()).isEqualTo("bob");
    }

    @Test
    void listRecent_humanChange_includesChangerUsername() {
        User target = User.builder().id(1L).username("bob").build();
        User admin = User.builder().id(2L).username("owner").build();
        RoleAuditLog log = RoleAuditLog.builder().id(1L).targetUser(target).changedBy(admin)
                .roleName("ROLE_ADMIN").action(RoleAuditAction.GRANTED).source(RoleAuditSource.MANUAL).build();
        when(roleAuditLogRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        List<RoleAuditLogDto> result = roleAuditService.listRecent();

        assertThat(result.get(0).changedById()).isEqualTo(2L);
        assertThat(result.get(0).changedByUsername()).isEqualTo("owner");
    }

    @Test
    void listForUser_delegatesToRepositoryById() {
        when(roleAuditLogRepository.findByTargetUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        List<RoleAuditLogDto> result = roleAuditService.listForUser(1L);

        assertThat(result).isEmpty();
        verify(roleAuditLogRepository).findByTargetUser_IdOrderByCreatedAtDesc(1L);
    }
}
