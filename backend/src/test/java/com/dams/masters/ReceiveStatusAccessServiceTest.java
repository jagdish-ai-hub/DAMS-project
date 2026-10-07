package com.dams.masters;

import com.dams.common.exception.DamsException;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveBusinessStatusRole;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRoleRepository;
import com.dams.masters.service.ReceiveStatusAccessService;
import com.dams.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which roles may set which job-card business status. The dealership's split is Cashier 3 /
 * Accountant 4 / Finance 7, but that lives in data — these tests prove the filtering and the
 * guard, not the particular list.
 */
@ExtendWith(MockitoExtension.class)
class ReceiveStatusAccessServiceTest {

    private static final long ORG = 1L;

    @Mock private ReceiveBusinessStatusRepository statusRepo;
    @Mock private ReceiveBusinessStatusRoleRepository roleRepo;

    private ReceiveStatusAccessService service() {
        return new ReceiveStatusAccessService(statusRepo, roleRepo);
    }

    @Test
    void selectableBy_returnsOnlyTheStatusesGrantedToThatRole() {
        when(statusRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of(
            status(1L, "Received", false, true),
            status(5L, "Closed", false, true)));
        when(roleRepo.findByOrgId(ORG)).thenReturn(List.of(
            grant(1L, Role.CASHIER),
            grant(5L, Role.FINANCE_MANAGER)));

        assertThat(service().selectableBy(ORG, Role.CASHIER))
            .extracting(ReceiveBusinessStatus::getName)
            .containsExactly("Received");
    }

    @Test
    void selectableBy_dropsDeprecatedStatuses_soNobodyPicksThemForNewWork() {
        // rev 65: a deprecated status is still active and granted, but is never offered for a new choice.
        // Records already on it keep showing it — the screen adds that one value itself.
        when(statusRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of(
            status(1L, "Received", false, true),
            status(91L, "WIP", true, true)));
        when(roleRepo.findByOrgId(ORG)).thenReturn(List.of(
            grant(1L, Role.CASHIER),
            grant(91L, Role.CASHIER)));

        assertThat(service().selectableBy(ORG, Role.CASHIER))
            .extracting(ReceiveBusinessStatus::getName)
            .containsExactly("Received");
    }

    @Test
    void selectableBy_omitsInactiveStatusesEvenWhenTheRoleIsGrantedThem() {
        when(statusRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of(
            status(1L, "Received", false, true),
            status(2L, "Retired label", false, false)));
        when(roleRepo.findByOrgId(ORG)).thenReturn(List.of(
            grant(1L, Role.CASHIER),
            grant(2L, Role.CASHIER)));

        assertThat(service().selectableBy(ORG, Role.CASHIER))
            .extracting(ReceiveBusinessStatus::getName)
            .containsExactly("Received");
    }

    @Test
    void requireMaySet_namesTheStatusAndRoleWhenTheGrantIsMissing() {
        ReceiveBusinessStatus closed = status(5L, "Claim Received", false, true);
        when(roleRepo.existsByOrgIdAndStatusIdAndRole(ORG, 5L, Role.CASHIER)).thenReturn(false);

        assertThatThrownBy(() -> service().requireMaySet(ORG, Role.CASHIER, closed))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Claim Received")
            .hasMessageContaining("CASHIER");
    }

    @Test
    void replaceRoles_refusesAnEmptySetBecauseNobodyCouldEverPickTheStatus() {
        assertThatThrownBy(() -> service().replaceRoles(ORG, 5L, Set.of()))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("at least one role");
        verify(roleRepo, never()).deleteByOrgIdAndStatusId(ORG, 5L);
    }

    @Test
    void replaceRoles_refusesARoleThatNeverSetsAStatus() {
        assertThatThrownBy(() -> service().replaceRoles(ORG, 5L, Set.of(Role.OWNER)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("OWNER");
        verify(roleRepo, never()).deleteByOrgIdAndStatusId(ORG, 5L);
    }

    @Test
    void replaceRoles_withTheSameRoles_writesNothing_soARenameCannotHitTheUniqueKey() {
        // The Masters form re-sends the roles on every save, even a rename. Deleting and re-inserting the
        // same (status, role) rows violated UNIQUE (status_id, role) -> "This change conflicts with an
        // existing record". Nothing may be deleted or inserted when nothing changed.
        ReceiveBusinessStatusRole cashier = grant(5L, Role.CASHIER);
        ReceiveBusinessStatusRole accountant = grant(5L, Role.ACCOUNTANT);
        when(roleRepo.findByOrgIdAndStatusId(ORG, 5L)).thenReturn(List.of(cashier, accountant));

        service().replaceRoles(ORG, 5L, Set.of(Role.CASHIER, Role.ACCOUNTANT));

        verify(roleRepo, never()).save(org.mockito.ArgumentMatchers.any(ReceiveBusinessStatusRole.class));
        verify(roleRepo, never()).deleteAll(org.mockito.ArgumentMatchers.anyIterable());
        verify(roleRepo, never()).deleteByOrgIdAndStatusId(ORG, 5L);
    }

    @Test
    void replaceRoles_removesWhatWentAndAddsWhatIsNew_deletesFirst() {
        ReceiveBusinessStatusRole cashier = grant(5L, Role.CASHIER);
        ReceiveBusinessStatusRole accountant = grant(5L, Role.ACCOUNTANT);
        when(roleRepo.findByOrgIdAndStatusId(ORG, 5L)).thenReturn(List.of(cashier, accountant));

        service().replaceRoles(ORG, 5L, Set.of(Role.CASHIER, Role.FINANCE_MANAGER));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(roleRepo);
        order.verify(roleRepo).deleteAll(List.of(accountant));   // Accountant removed — Cashier row untouched
        order.verify(roleRepo).flush();                          // deletes reach the DB before any insert
        org.mockito.ArgumentCaptor<ReceiveBusinessStatusRole> saved =
            org.mockito.ArgumentCaptor.forClass(ReceiveBusinessStatusRole.class);
        order.verify(roleRepo).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.FINANCE_MANAGER);
        verify(roleRepo, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any(ReceiveBusinessStatusRole.class));
    }

    @Test
    void replaceRoles_onAStatusWithNoGrantsYet_justInserts() {
        when(roleRepo.findByOrgIdAndStatusId(ORG, 5L)).thenReturn(List.of());

        service().replaceRoles(ORG, 5L, Set.of(Role.FINANCE_MANAGER));

        verify(roleRepo, never()).deleteAll(org.mockito.ArgumentMatchers.anyIterable());
        verify(roleRepo).save(org.mockito.ArgumentMatchers.any(ReceiveBusinessStatusRole.class));
    }

    // --- fixtures ---

    private static ReceiveBusinessStatus status(long id, String name, boolean deprecated, boolean active) {
        ReceiveBusinessStatus s = new ReceiveBusinessStatus();
        ReflectionTestUtils.setField(s, "id", id);
        s.setOrgId(ORG);
        s.setName(name);
        s.setActive(active);
        s.setDeprecated(deprecated);
        return s;
    }

    private static ReceiveBusinessStatusRole grant(long statusId, Role role) {
        ReceiveBusinessStatusRole g = new ReceiveBusinessStatusRole();
        g.setOrgId(ORG);
        g.setStatusId(statusId);
        g.setRole(role);
        return g;
    }
}
