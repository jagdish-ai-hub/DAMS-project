package com.dams.jobcard;

import com.dams.audit.service.AuditService;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.JobCardService;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Job cards are branch-scoped reads and writes: with the cashier toggle OFF, another
 * branch's card is invisible even by id — for both viewing and patching references.
 */
@ExtendWith(MockitoExtension.class)
class JobCardBranchScopeTest {

    private static final long ORG = 1L;

    @Mock private JobCardRepository jobCardRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private ReceiveCategoryRepository categoryRepo;
    @Mock private ReceiveBusinessStatusRepository statusRepo;
    @Mock private ClaimTypeRepository claimTypeRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private BranchScope branchScope;
    @Mock private AuditService auditService;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private ReceivePaymentGuard paymentGuard;

    private JobCardService service;

    @BeforeEach
    void setUp() {
        service = new JobCardService(jobCardRepo, customerRepo, vehicleRepo, branchRepo,
            categoryRepo, statusRepo, claimTypeRepo, userRepo, branchScope, auditService,
            pendingAmountCalculator, claimCloseRepo, paymentGuard);
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void get_refusesAnotherBranchJobCard() {
        when(jobCardRepo.findByIdAndOrgId(9L, ORG)).thenReturn(Optional.of(jobCard(99L)));
        when(branchScope.canSeeBranch(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.get(9L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("outside your access");
    }

    @Test
    void patch_refusesAnotherBranchJobCard() {
        when(jobCardRepo.findByIdAndOrgId(9L, ORG)).thenReturn(Optional.of(jobCard(99L)));
        when(branchScope.canSeeBranch(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.patch(9L, new com.dams.jobcard.dto.JobCardPatchRequest()))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("outside your access");
    }

    private static JobCard jobCard(long branchId) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", 9L);
        jc.setOrgId(ORG);
        jc.setBranchId(branchId);
        jc.setCustomerId(3L);
        jc.setCategoryId(5L);
        jc.setBusinessStatusId(6L);
        return jc;
    }
}
