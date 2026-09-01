package com.dams.jobcard;

import com.dams.attachment.service.AttachmentService;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.jobcard.dto.CloseClaimRequest;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.ClaimCloseService;
import com.dams.jobcard.service.JobCardService;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The FM's final claim close: writes the immutable {@code claim_close} row, flips
 * {@code settled} on every open receive document of the job card, and refuses when the money
 * isn't fully through the maker-checker flow or when an override has no reason.
 */
@ExtendWith(MockitoExtension.class)
class ClaimCloseServiceTest {

    private static final long ORG = 1L;
    private static final long FM_ID = 60L;
    private static final long BRANCH = 3L;
    private static final long JC_ID = 70L;
    private static final long CLAIM_CATEGORY = 9L;

    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ReceiveCategoryRepository categoryRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private com.dams.user.repository.AppUserRepository userRepo;
    @Mock private BranchScope branchScope;
    @Mock private AttachmentService attachmentService;
    @Mock private AuditService auditService;
    @Mock private JobCardService jobCardService;

    private ClaimCloseService service;

    @BeforeEach
    void setUp() {
        service = new ClaimCloseService(claimCloseRepo, jobCardRepo, receiveDocumentRepo, settlementLineRepo,
            categoryRepo, branchRepo, userRepo, branchScope, attachmentService, auditService, jobCardService);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(FM_ID);
        lenient().when(userRepo.findByIdAndOrganization_Id(FM_ID, ORG)).thenReturn(Optional.of(user(Role.FINANCE_MANAGER)));
        lenient().when(jobCardRepo.findByIdAndOrgId(JC_ID, ORG)).thenReturn(Optional.of(jobCard()));
        lenient().when(categoryRepo.findByIdAndOrgId(CLAIM_CATEGORY, ORG)).thenReturn(Optional.of(category(true)));
        lenient().when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, JC_ID)).thenReturn(false);
        lenient().when(settlementLineRepo.sumAmountForJobCard(ORG, JC_ID)).thenReturn(new BigDecimal("13000"));
        lenient().when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(eq(ORG), any()))
            .thenReturn(List.of());
        lenient().when(claimCloseRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(receiveDocumentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void closeClaim_writesRow_flipsSettledOnEveryOpenDoc_andAudits() {
        ReceiveDocument d1 = doc(101L, WorkflowStatus.APPROVED, false);
        ReceiveDocument d2 = doc(102L, WorkflowStatus.APPROVED, false);
        when(receiveDocumentRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(ORG, JC_ID)).thenReturn(List.of(d1, d2));

        service.closeClaim(JC_ID, new CloseClaimRequest(new BigDecimal("13000"), null));

        ArgumentCaptor<ClaimClose> saved = ArgumentCaptor.forClass(ClaimClose.class);
        verify(claimCloseRepo).save(saved.capture());
        assertThat(saved.getValue().getFinalAmount()).isEqualByComparingTo("13000");
        assertThat(saved.getValue().isOverridden()).isFalse();
        assertThat(saved.getValue().getClosedBy()).isEqualTo(FM_ID);

        assertThat(d1.isSettled()).isTrue();
        assertThat(d2.isSettled()).isTrue();
        verify(attachmentService, times(2)).freezeReceiveDocument(eq(ORG), any(), any());
        verify(auditService, times(2)).recordUserEvent(eq("ReceiveDocument"), any(), eq(BRANCH),
            eq(EventType.SETTLED), eq(FM_ID), any());
        verify(auditService).recordUserEvent(eq("JobCard"), eq(JC_ID), eq(BRANCH),
            eq(EventType.CLOSED), eq(FM_ID), any());
        verify(jobCardService).get(JC_ID);
    }

    @Test
    void closeClaim_badRequest_whenOverriddenWithNoReason() {
        when(receiveDocumentRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(ORG, JC_ID))
            .thenReturn(List.of(doc(101L, WorkflowStatus.APPROVED, false)));

        assertThatThrownBy(() -> service.closeClaim(JC_ID, new CloseClaimRequest(new BigDecimal("12400"), "  ")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("reason is required");
        verify(claimCloseRepo, never()).save(any());
    }

    @Test
    void closeClaim_overridden_withReason_recordsTheReason() {
        when(receiveDocumentRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(ORG, JC_ID))
            .thenReturn(List.of(doc(101L, WorkflowStatus.APPROVED, false)));

        service.closeClaim(JC_ID, new CloseClaimRequest(new BigDecimal("12400"),
            "Eicher partial settlement — ₹600 written off"));

        ArgumentCaptor<ClaimClose> saved = ArgumentCaptor.forClass(ClaimClose.class);
        verify(claimCloseRepo).save(saved.capture());
        assertThat(saved.getValue().isOverridden()).isTrue();
        assertThat(saved.getValue().getFinalAmount()).isEqualByComparingTo("12400");
        assertThat(saved.getValue().getOverrideReason()).contains("written off");
    }

    @Test
    void closeClaim_conflict_whenCategoryIsNotAClaim() {
        when(categoryRepo.findByIdAndOrgId(CLAIM_CATEGORY, ORG)).thenReturn(Optional.of(category(false)));

        assertThatThrownBy(() -> service.closeClaim(JC_ID, new CloseClaimRequest(BigDecimal.ZERO, null)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("not a warranty / AMC / CG claim");
    }

    @Test
    void closeClaim_conflict_whenAReceiveDocumentIsNotApproved() {
        when(receiveDocumentRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(ORG, JC_ID))
            .thenReturn(List.of(doc(101L, WorkflowStatus.APPROVED, false), doc(102L, WorkflowStatus.VERIFIED, false)));

        assertThatThrownBy(() -> service.closeClaim(JC_ID, new CloseClaimRequest(new BigDecimal("13000"), null)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("must be approved");
        verify(claimCloseRepo, never()).save(any());
    }

    @Test
    void closeClaim_conflict_whenAlreadyClosed() {
        when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, JC_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.closeClaim(JC_ID, new CloseClaimRequest(BigDecimal.ZERO, null)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("already closed");
    }

    @Test
    void closeClaim_forbidden_whenCallerIsNotFinanceManager() {
        when(userRepo.findByIdAndOrganization_Id(FM_ID, ORG)).thenReturn(Optional.of(user(Role.ACCOUNTANT)));

        assertThatThrownBy(() -> service.closeClaim(JC_ID, new CloseClaimRequest(BigDecimal.ZERO, null)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a Finance Manager");
    }

    // ---- fixtures ----

    private static AppUser user(Role role) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", FM_ID);
        u.setRole(role);
        return u;
    }

    private static JobCard jobCard() {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", JC_ID);
        jc.setOrgId(ORG);
        jc.setBranchId(BRANCH);
        jc.setCustomerId(42L);
        jc.setCategoryId(CLAIM_CATEGORY);
        jc.setBusinessStatusId(5L);
        return jc;
    }

    private static ReceiveCategory category(boolean claim) {
        ReceiveCategory c = new ReceiveCategory();
        ReflectionTestUtils.setField(c, "id", CLAIM_CATEGORY);
        c.setName("Warranty");
        c.setClaim(claim);
        return c;
    }

    private static ReceiveDocument doc(long id, WorkflowStatus status, boolean settled) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH);
        d.setJobCardId(JC_ID);
        d.setDocumentNo("OOR-JUL26-R-0" + id);
        d.setWorkflowStatus(status);
        d.setSettled(settled);
        d.setCreatedBy(7L);
        return d;
    }
}
