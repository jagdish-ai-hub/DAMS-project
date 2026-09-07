package com.dams.admin;

import com.dams.admin.service.AdminOrgService;
import com.dams.admin.service.OrganizationPurgeService;
import com.dams.common.exception.DamsException;
import com.dams.email.EmailService;
import com.dams.masters.service.MasterProvisioningService;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * plan.md rev7: deleting an org with live money is refused — purge is only for
 * mis-onboarded orgs. Deactivation, not deletion, is the operational path.
 */
@ExtendWith(MockitoExtension.class)
class OrgDeleteGuardTest {

    @Mock private OrganizationRepository orgRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private EmailService emailService;
    @Mock private OrganizationPurgeService purgeService;
    @Mock private MasterProvisioningService masterProvisioningService;

    @InjectMocks
    private AdminOrgService service;

    @Test
    void deleteOrganization_refusesOrgWithTransactionalData() {
        Organization org = new Organization("Live Dealership");
        ReflectionTestUtils.setField(org, "id", 9L);
        when(orgRepo.findById(9L)).thenReturn(Optional.of(org));
        when(purgeService.hasTransactionalData(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteOrganization(9L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("transactional documents");
        verify(purgeService, never()).purgeChildren(9L);
        verify(orgRepo, never()).delete(org);
    }

    @Test
    void deleteOrganization_purgesOrgWithNoTransactions() {
        Organization org = new Organization("Mis-onboarded");
        ReflectionTestUtils.setField(org, "id", 9L);
        when(orgRepo.findById(9L)).thenReturn(Optional.of(org));
        when(purgeService.hasTransactionalData(9L)).thenReturn(false);

        service.deleteOrganization(9L);

        verify(purgeService).purgeChildren(9L);
        verify(orgRepo).delete(org);
    }
}
