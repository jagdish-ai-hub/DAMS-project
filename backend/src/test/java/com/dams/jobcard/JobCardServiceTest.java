package com.dams.jobcard;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.dto.JobCardPatchRequest;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.JobCardService;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Job-card rules for Stage 3: a cashier's job card always posts under their home branch,
 * creation is audited, and a category change on PATCH writes a CATEGORY_CHANGED event with
 * the before/after ids. Test names describe the behaviour proven — see AGENT.md.
 */
@ExtendWith(MockitoExtension.class)
class JobCardServiceTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long HOME_BRANCH = 5L;

    @Mock private JobCardRepository jobCardRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private ReceiveCategoryRepository categoryRepo;
    @Mock private ReceiveBusinessStatusRepository statusRepo;
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
            categoryRepo, statusRepo, userRepo, branchScope, auditService,
            pendingAmountCalculator, claimCloseRepo, paymentGuard);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(CASHIER_ID);
        lenient().when(pendingAmountCalculator.forJobCard(any(JobCard.class)))
            .thenReturn(java.math.BigDecimal.ZERO);
        lenient().when(claimCloseRepo.findByOrgIdAndJobCardId(any(), any())).thenReturn(Optional.empty());
        lenient().when(jobCardRepo.save(any(JobCard.class))).thenAnswer(inv -> {
            JobCard jc = inv.getArgument(0);
            if (jc.getId() == null) {
                ReflectionTestUtils.setField(jc, "id", 100L);
            }
            return jc;
        });
        // read-model lookups used by toResponse
        lenient().when(branchRepo.findByIdAndOrgId(HOME_BRANCH, ORG)).thenReturn(Optional.of(branch(HOME_BRANCH, "OOR")));
        lenient().when(customerRepo.findByIdAndOrgId(eq(42L), eq(ORG))).thenReturn(Optional.of(customer(42L)));
        lenient().when(categoryRepo.findByIdAndOrgId(eq(3L), eq(ORG))).thenReturn(Optional.of(category(3L, "Workshop", false)));
        lenient().when(categoryRepo.findByIdAndOrgId(eq(9L), eq(ORG))).thenReturn(Optional.of(category(9L, "Warranty", true)));
        lenient().when(statusRepo.findByIdAndOrgId(eq(4L), eq(ORG))).thenReturn(Optional.of(status(4L, "WIP")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_asCashier_forcesHomeBranch_andIgnoresRequestedBranch() {
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG))
            .thenReturn(Optional.of(cashierWithHomeBranch()));

        JobCardCreateRequest req = new JobCardCreateRequest();
        req.setCustomerId(42L);
        req.setBranchId(999L);        // some other branch — must be ignored
        req.setCategoryId(3L);
        req.setBusinessStatusId(4L);

        service.create(req);

        ArgumentCaptor<JobCard> captor = ArgumentCaptor.forClass(JobCard.class);
        verify(jobCardRepo).save(captor.capture());
        assertThat(captor.getValue().getBranchId()).isEqualTo(HOME_BRANCH);
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG);
        verify(branchRepo, never()).findByIdAndOrgId(eq(999L), any());
    }

    @Test
    void create_writesCreatedAuditEvent() {
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG))
            .thenReturn(Optional.of(cashierWithHomeBranch()));

        JobCardCreateRequest req = new JobCardCreateRequest();
        req.setCustomerId(42L);
        req.setCategoryId(3L);
        req.setBusinessStatusId(4L);

        service.create(req);

        verify(auditService).recordUserEvent(eq("JobCard"), eq(100L), any(), eq(EventType.CREATED),
            eq(CASHIER_ID), any());
    }

    @Test
    void create_withNoCustomerIdAndNoName_throwsBadRequest() {
        JobCardCreateRequest req = new JobCardCreateRequest();
        req.setCategoryId(3L);
        req.setBusinessStatusId(4L);

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("customerId or customerName");
    }

    @Test
    void create_b2bWithoutGst_isRejected() {
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG))
            .thenReturn(Optional.of(cashierWithHomeBranch()));

        JobCardCreateRequest req = new JobCardCreateRequest();
        req.setCustomerId(42L);
        req.setCategoryId(3L);
        req.setBusinessStatusId(4L);
        req.setB2b(true);            // no GST number

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("GST number is required");
    }

    @Test
    void create_b2bWithGst_isStored() {
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG))
            .thenReturn(Optional.of(cashierWithHomeBranch()));

        JobCardCreateRequest req = new JobCardCreateRequest();
        req.setCustomerId(42L);
        req.setCategoryId(3L);
        req.setBusinessStatusId(4L);
        req.setB2b(true);
        req.setGstNo("21ABCDE1234F1Z5");

        service.create(req);

        ArgumentCaptor<JobCard> captor = ArgumentCaptor.forClass(JobCard.class);
        verify(jobCardRepo).save(captor.capture());
        assertThat(captor.getValue().isB2b()).isTrue();
        assertThat(captor.getValue().getGstNo()).isEqualTo("21ABCDE1234F1Z5");
    }

    @Test
    void patch_categoryChange_writesCategoryChangedAudit_withBeforeAndAfter() {
        JobCard existing = new JobCard();
        ReflectionTestUtils.setField(existing, "id", 100L);
        existing.setOrgId(ORG);
        existing.setBranchId(HOME_BRANCH);
        existing.setCustomerId(42L);
        existing.setCategoryId(3L);
        existing.setBusinessStatusId(4L);
        when(jobCardRepo.findByIdAndOrgId(100L, ORG)).thenReturn(Optional.of(existing));

        JobCardPatchRequest patch = new JobCardPatchRequest();
        patch.setCategoryId(9L);      // Workshop -> Warranty

        service.patch(100L, patch);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordUserEvent(eq("JobCard"), eq(100L), any(), eq(EventType.CATEGORY_CHANGED),
            eq(CASHIER_ID), detail.capture());
        assertThat(detail.getValue()).containsEntry("before", 3L).containsEntry("after", 9L);
        assertThat(existing.getCategoryId()).isEqualTo(9L);
    }

    @Test
    void patch_withSameCategory_doesNotAudit() {
        JobCard existing = new JobCard();
        ReflectionTestUtils.setField(existing, "id", 100L);
        existing.setOrgId(ORG);
        existing.setBranchId(HOME_BRANCH);
        existing.setCustomerId(42L);
        existing.setCategoryId(3L);
        existing.setBusinessStatusId(4L);
        when(jobCardRepo.findByIdAndOrgId(100L, ORG)).thenReturn(Optional.of(existing));

        JobCardPatchRequest patch = new JobCardPatchRequest();
        patch.setCategoryId(3L);      // unchanged
        patch.setInvoiceNo("INV-1");

        service.patch(100L, patch);

        verify(auditService, never()).recordUserEvent(any(), any(), any(), any(), any(), any());
        assertThat(existing.getInvoiceNo()).isEqualTo("INV-1");
    }

    // --- fixtures ---

    private static AppUser cashierWithHomeBranch() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(HOME_BRANCH);
        return u;
    }

    private static Branch branch(long id, String code) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCode(code);
        b.setName(code + " branch");
        b.setActive(true);
        return b;
    }

    private static Customer customer(long id) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", id);
        c.setOrgId(ORG);
        c.setName("Test Customer");
        return c;
    }

    private static ReceiveCategory category(long id, String name, boolean claim) {
        ReceiveCategory c = new ReceiveCategory();
        ReflectionTestUtils.setField(c, "id", id);
        c.setOrgId(ORG);
        c.setName(name);
        c.setActive(true);
        c.setClaim(claim);
        return c;
    }

    private static ReceiveBusinessStatus status(long id, String name) {
        ReceiveBusinessStatus s = new ReceiveBusinessStatus();
        ReflectionTestUtils.setField(s, "id", id);
        s.setOrgId(ORG);
        s.setName(name);
        s.setActive(true);
        return s;
    }
}
