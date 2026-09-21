package com.dams.review;

import com.dams.attachment.service.AttachmentService;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.expense.service.ExpenseDocumentService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.review.dto.BulkApproveResponse;
import com.dams.review.dto.ReviewQueueItem;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.organization.entity.Organization;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receive.service.ReceiveDocumentService;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.review.service.ReviewGuard;
import com.dams.review.service.ReviewService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Review transitions: the Accountant's verify / query / reject / line-override / close-expense
 * on SUBMITTED documents, and the Finance Manager's approve / query / reject on VERIFIED ones.
 * Guard behaviour itself is covered by {@link ReviewGuardTest}.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final long ORG = 1L;
    private static final long ACTOR_ID = 50L;
    private static final long BRANCH = 3L;
    private static final long R_ID = 500L;
    private static final long E_ID = 600L;
    private static final long C_ID = 700L;

    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private ReceiveCategoryRepository receiveCategoryRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;
    @Mock private ReceiverRepository receiverRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private BranchScope branchScope;
    @Mock private ReviewGuard guard;
    @Mock private AuditService auditService;
    @Mock private AttachmentService attachmentService;
    @Mock private ReceiveDocumentService receiveDocumentService;
    @Mock private ExpenseDocumentService expenseDocumentService;
    @Mock private com.dams.cash.repository.CashDocumentRepository cashDocumentRepo;
    @Mock private com.dams.cash.service.CashDocumentService cashDocumentService;
    @Mock private com.dams.organization.repository.OrganizationRepository organizationRepo;
    @Mock private com.dams.masters.repository.SettlementModeRepository settlementModeRepo;
    @Mock private com.dams.masters.repository.ReceiveBusinessStatusRepository receiveBusinessStatusRepo;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(receiveDocumentRepo, settlementLineRepo, expenseDocumentRepo, expenseLineRepo,
            jobCardRepo, customerRepo, receiveCategoryRepo, expenseCategoryRepo, receiverRepo, claimCloseRepo,
            branchRepo, branchScope, guard, auditService, attachmentService, receiveDocumentService, expenseDocumentService,
            cashDocumentRepo, cashDocumentService, organizationRepo, settlementModeRepo, receiveBusinessStatusRepo);
        TenantContext.setOrgId(ORG);
        lenient().when(guard.requireAccountant()).thenReturn(actor(Role.ACCOUNTANT));
        lenient().when(guard.requireFinanceManager()).thenReturn(actor(Role.FINANCE_MANAGER));
        lenient().when(receiveDocumentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(expenseDocumentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(cashDocumentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------------------------------------------------- accountant: receipts

    @Test
    void verifyReceipt_movesSubmittedToVerified_andAudits() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.verifyReceipt(R_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.VERIFIED);
        // a review action never touches last_modified_by — it tracks the maker
        assertThat(doc.getLastModifiedBy()).isEqualTo(7L);
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.VERIFIED), eq(ACTOR_ID), any());
    }

    @Test
    void verifyReceipt_conflict_whenNotSubmitted() {
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(receiveDoc(WorkflowStatus.VERIFIED)));
        assertThatThrownBy(() -> service.verifyReceipt(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("VERIFIED");
        verify(receiveDocumentRepo, never()).save(any());
    }

    @Test
    void verifyReceipt_propagatesMakerCheckerFailureFromGuard() {
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(receiveDoc(WorkflowStatus.SUBMITTED)));
        doThrow(DamsException.conflict("you created or last modified it"))
            .when(guard).requireCanReview(any(), eq(BRANCH), any(), any(), any());
        assertThatThrownBy(() -> service.verifyReceipt(R_ID)).isInstanceOf(DamsException.class);
        verify(receiveDocumentRepo, never()).save(any());
    }

    @Test
    void queryReceipt_asAccountant_movesToQueried_withTheNoteOnTheAudit() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.queryReceipt(R_ID, "Amount does not match the invoice");

        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.QUERIED);
        ArgumentCaptor<Map<String, Object>> detail = captor();
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.QUERIED), eq(ACTOR_ID), detail.capture());
        assertThat(detail.getValue()).containsEntry("note", "Amount does not match the invoice");
    }

    @Test
    void rejectReceipt_asAccountant_movesToRejected_andRefreshesSettlement() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.rejectReceipt(R_ID, "Duplicate of R-004");

        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.REJECTED);
        verify(receiveDocumentService).refreshSettlement(R_ID);
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.REJECTED), eq(ACTOR_ID), any());
    }

    @Test
    void overrideReceiptLine_stampsTheTrail_keepsOriginal_andRefreshesSettlement() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        SettlementLine line = settlementLine(1, new BigDecimal("800"));
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdAndLineNo(ORG, R_ID, 1)).thenReturn(Optional.of(line));

        service.overrideReceiptLine(R_ID, 1, new BigDecimal("1000"), "Rate corrected");

        assertThat(line.getAmount()).isEqualByComparingTo("1000");
        assertThat(line.getOriginalAmount()).isEqualByComparingTo("800");
        assertThat(line.getOverriddenBy()).isEqualTo(ACTOR_ID);
        assertThat(line.getOverriddenAt()).isNotNull();
        verify(receiveDocumentService).refreshSettlement(R_ID);
        ArgumentCaptor<Map<String, Object>> detail = captor();
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.OVERRIDE), eq(ACTOR_ID), detail.capture());
        assertThat(detail.getValue()).containsEntry("reason", "Rate corrected");
    }

    @Test
    void overrideReceiptLine_badRequest_whenAmountUnchanged() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        SettlementLine line = settlementLine(1, new BigDecimal("800"));
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdAndLineNo(ORG, R_ID, 1)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.overrideReceiptLine(R_ID, 1, new BigDecimal("800.00"), "no-op"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("same as the current amount");
    }

    @Test
    void overrideInvoiceAmount_stampsTheJobCard_andAuditsOnTheJobCard_notTheReceipt() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        JobCard jc = claimJobCard();
        jc.setInvoiceAmount(new BigDecimal("21000"));
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(jc));
        when(jobCardRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.overrideInvoiceAmount(R_ID, new BigDecimal("19500"), "Corrected against the signed invoice copy");

        assertThat(jc.getInvoiceAmount()).isEqualByComparingTo("19500");
        ArgumentCaptor<Map<String, Object>> detail = captor();
        // Recorded against the JobCard, not the ReceiveDocument — invoice amount outlives any one receipt.
        verify(auditService).recordUserEvent(eq("JobCard"), eq(11L), eq(BRANCH),
            eq(EventType.OVERRIDE), eq(ACTOR_ID), detail.capture());
        assertThat(detail.getValue())
            .containsEntry("amountBefore", new BigDecimal("21000"))
            .containsEntry("amountAfter", new BigDecimal("19500"))
            .containsEntry("reason", "Corrected against the signed invoice copy")
            .containsEntry("receiptId", R_ID);
    }

    @Test
    void overrideInvoiceAmount_allowsSettingItForTheFirstTime_whenNoneExistsYet() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        JobCard jc = claimJobCard();
        jc.setInvoiceAmount(null);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(jc));
        when(jobCardRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.overrideInvoiceAmount(R_ID, new BigDecimal("5000"), "Invoice copy received late");

        assertThat(jc.getInvoiceAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void overrideInvoiceAmount_badRequest_whenAmountUnchanged() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        JobCard jc = claimJobCard();
        jc.setInvoiceAmount(new BigDecimal("21000"));
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(jc));

        assertThatThrownBy(() -> service.overrideInvoiceAmount(R_ID, new BigDecimal("21000.00"), "no-op"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("same as the current invoice amount");
        verify(jobCardRepo, never()).save(any());
    }

    @Test
    void overrideInvoiceAmount_conflict_whenReceiptIsNotSubmitted() {
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(receiveDoc(WorkflowStatus.VERIFIED)));
        assertThatThrownBy(() -> service.overrideInvoiceAmount(R_ID, new BigDecimal("5000"), "too late"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("VERIFIED");
        verify(jobCardRepo, never()).save(any());
    }

    @Test
    void overrideInvoiceAmount_propagatesMakerCheckerFailureFromGuard() {
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(receiveDoc(WorkflowStatus.SUBMITTED)));
        doThrow(DamsException.conflict("you created or last modified it"))
            .when(guard).requireCanReview(any(), eq(BRANCH), any(), any(), any());
        assertThatThrownBy(() -> service.overrideInvoiceAmount(R_ID, new BigDecimal("5000"), "reason"))
            .isInstanceOf(DamsException.class);
        verify(jobCardRepo, never()).save(any());
    }

    // ---------------------------------------------------- accountant: direct approve

    private void stubDirectApproveOn() {
        when(organizationRepo.findById(ORG)).thenReturn(Optional.of(organization(true)));
    }

    private void stubEligible() {
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(plainJobCard()));
        when(receiveBusinessStatusRepo.findByOrgIdAndNameIgnoreCase(ORG, "Credit")).thenReturn(Optional.of(creditStatus()));
        when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(cashMode()));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(ORG, List.of(R_ID)))
            .thenReturn(List.of(settlementLine(1, new BigDecimal("500"))));
    }

    @Test
    void directApproveReceipt_movesSubmittedStraightToApproved_oneAuditEvent() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        stubDirectApproveOn();
        stubEligible();

        service.directApproveReceipt(R_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
        ArgumentCaptor<Map<String, Object>> detail = captor();
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.APPROVED), eq(ACTOR_ID), detail.capture());
        assertThat(detail.getValue()).containsEntry("directByAccountant", true);
        // exactly one audit call — no separate VERIFIED row on the direct-approve path
        verify(auditService, times(1)).recordUserEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void directApproveReceipt_forbidden_whenOrgToggleIsOff() {
        when(organizationRepo.findById(ORG)).thenReturn(Optional.of(organization(false)));

        assertThatThrownBy(() -> service.directApproveReceipt(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("off for this organization");
        verify(receiveDocumentRepo, never()).save(any());
    }

    @Test
    void directApproveReceipt_conflict_whenJobCardIsAClaim() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        stubDirectApproveOn();
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(claimJobCard()));
        lenient().when(receiveBusinessStatusRepo.findByOrgIdAndNameIgnoreCase(ORG, "Credit")).thenReturn(Optional.of(creditStatus()));
        lenient().when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(cashMode()));
        lenient().when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(ORG, List.of(R_ID)))
            .thenReturn(List.of(settlementLine(1, new BigDecimal("500"))));

        assertThatThrownBy(() -> service.directApproveReceipt(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("not eligible");
        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.SUBMITTED);
    }

    @Test
    void directApproveReceipt_conflict_whenBusinessStatusIsCredit() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        JobCard jc = plainJobCard();
        jc.setBusinessStatusId(99L);   // the Credit status id
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        stubDirectApproveOn();
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(jc));
        when(receiveBusinessStatusRepo.findByOrgIdAndNameIgnoreCase(ORG, "Credit")).thenReturn(Optional.of(creditStatus()));
        lenient().when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(cashMode()));
        lenient().when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(ORG, List.of(R_ID)))
            .thenReturn(List.of(settlementLine(1, new BigDecimal("500"))));

        assertThatThrownBy(() -> service.directApproveReceipt(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("not eligible");
    }

    @Test
    void directApproveReceipt_conflict_whenAnyLineIsNotCashMode() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        SettlementLine bankLine = settlementLine(1, new BigDecimal("500"));
        bankLine.setSettlementModeId(2L);   // bank, not cash
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        stubDirectApproveOn();
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(plainJobCard()));
        when(receiveBusinessStatusRepo.findByOrgIdAndNameIgnoreCase(ORG, "Credit")).thenReturn(Optional.of(creditStatus()));
        when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(cashMode()));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(ORG, List.of(R_ID)))
            .thenReturn(List.of(bankLine));

        assertThatThrownBy(() -> service.directApproveReceipt(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("not eligible");
    }

    @Test
    void directApproveEligibleReceipts_filtersOutClaimsAndCreditAndNonCashLines() {
        stubDirectApproveOn();
        when(branchScope.allowedBranchIds()).thenReturn(Optional.of(Set.of(BRANCH)));

        ReceiveDocument eligibleDoc = receiveDoc(WorkflowStatus.SUBMITTED);
        ReflectionTestUtils.setField(eligibleDoc, "id", 501L);
        eligibleDoc.setJobCardId(11L);

        ReceiveDocument claimDoc = receiveDoc(WorkflowStatus.SUBMITTED);
        ReflectionTestUtils.setField(claimDoc, "id", 502L);
        claimDoc.setJobCardId(12L);

        // claimJobCard() shares id 11 with plainJobCard() elsewhere in this file (it's "the
        // job card behind receiveDoc") — give this one id 12 so the two don't collide in the
        // id-keyed map jobCardsById() builds.
        JobCard claimJc = claimJobCard();
        ReflectionTestUtils.setField(claimJc, "id", 12L);

        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(
            ORG, WorkflowStatus.SUBMITTED, Set.of(BRANCH))).thenReturn(List.of(eligibleDoc, claimDoc));
        when(jobCardRepo.findByOrgIdAndIdIn(ORG, List.of(11L, 12L)))
            .thenReturn(List.of(plainJobCard(), claimJc));
        when(receiveBusinessStatusRepo.findByOrgIdAndNameIgnoreCase(ORG, "Credit")).thenReturn(Optional.of(creditStatus()));
        when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(cashMode()));
        SettlementLine line501 = settlementLine(1, new BigDecimal("500"));
        ReflectionTestUtils.setField(line501, "id", 9501L);
        line501.setReceiveDocumentId(501L);
        SettlementLine line502 = settlementLine(1, new BigDecimal("500"));
        ReflectionTestUtils.setField(line502, "id", 9502L);
        line502.setReceiveDocumentId(502L);
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(ORG, List.of(501L, 502L)))
            .thenReturn(List.of(line501, line502));
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of());
        when(receiveCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of());

        List<ReviewQueueItem> out = service.directApproveEligibleReceipts();

        assertThat(out).extracting(ReviewQueueItem::id).containsExactly(501L);
    }

    @Test
    void bulkDirectApproveReceipts_approvesEligible_skipsIneligible() {
        ReceiveDocument eligibleDoc = receiveDoc(WorkflowStatus.SUBMITTED);
        ReflectionTestUtils.setField(eligibleDoc, "id", 501L);
        ReceiveDocument claimDoc = receiveDoc(WorkflowStatus.SUBMITTED);
        ReflectionTestUtils.setField(claimDoc, "id", 502L);
        claimDoc.setJobCardId(12L);

        stubDirectApproveOn();
        when(receiveDocumentRepo.findByIdAndOrgId(501L, ORG)).thenReturn(Optional.of(eligibleDoc));
        when(receiveDocumentRepo.findByIdAndOrgId(502L, ORG)).thenReturn(Optional.of(claimDoc));
        when(jobCardRepo.findByIdAndOrgId(11L, ORG)).thenReturn(Optional.of(plainJobCard()));
        when(jobCardRepo.findByIdAndOrgId(12L, ORG)).thenReturn(Optional.of(claimJobCard()));
        when(receiveBusinessStatusRepo.findByOrgIdAndNameIgnoreCase(ORG, "Credit")).thenReturn(Optional.of(creditStatus()));
        when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(cashMode()));
        SettlementLine line501 = settlementLine(1, new BigDecimal("500"));
        line501.setReceiveDocumentId(501L);
        SettlementLine line502 = settlementLine(1, new BigDecimal("500"));
        line502.setReceiveDocumentId(502L);
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(ORG, List.of(501L)))
            .thenReturn(List.of(line501));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(ORG, List.of(502L)))
            .thenReturn(List.of(line502));

        BulkApproveResponse res = service.bulkDirectApproveReceipts(List.of(501L, 502L));

        assertThat(res.approvedCount()).isEqualTo(1);
        assertThat(res.approvedIds()).containsExactly(501L);
        assertThat(res.skippedReasons()).hasSize(1);
        assertThat(eligibleDoc.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
        assertThat(claimDoc.getWorkflowStatus()).isEqualTo(WorkflowStatus.SUBMITTED);
    }

    // ---------------------------------------------------- accountant: expenses

    @Test
    void verifyExpense_movesSubmittedToVerified() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.SUBMITTED, false);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));

        service.verifyExpense(E_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.VERIFIED);
        verify(auditService).recordUserEvent(eq("ExpenseDocument"), eq(E_ID), eq(BRANCH),
            eq(EventType.VERIFIED), eq(ACTOR_ID), any());
    }

    @Test
    void overrideExpenseLine_recomputesOverLimit_andAudits() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.SUBMITTED, false);
        ExpenseLine line = expenseLine(1, new BigDecimal("1400"));
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdAndLineNo(ORG, E_ID, 1)).thenReturn(Optional.of(line));

        service.overrideExpenseLine(E_ID, 1, new BigDecimal("900"), "Coolie charge only");

        assertThat(line.getAmount()).isEqualByComparingTo("900");
        assertThat(line.getOriginalAmount()).isEqualByComparingTo("1400");
        verify(expenseDocumentService).recomputeOverLimit(ORG, doc);
        verify(auditService).recordUserEvent(eq("ExpenseDocument"), eq(E_ID), eq(BRANCH),
            eq(EventType.OVERRIDE), eq(ACTOR_ID), any());
    }

    @Test
    void closeExpense_fromVerified_whenNotOverLimit_closesAndFreezes() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.VERIFIED, false);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(ORG, E_ID)).thenReturn(java.util.List.of());

        service.closeExpense(E_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.CLOSED);
        verify(attachmentService).freezeExpenseDocument(eq(ORG), eq(E_ID), any());
        verify(auditService).recordUserEvent(eq("ExpenseDocument"), eq(E_ID), eq(BRANCH),
            eq(EventType.CLOSED), eq(ACTOR_ID), any());
    }

    @Test
    void closeExpense_conflict_whenStillSubmitted() {
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.SUBMITTED, false)));
        assertThatThrownBy(() -> service.closeExpense(E_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("verified or approved");
    }

    @Test
    void closeExpense_conflict_whenOverLimitAndOnlyVerified() {
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.VERIFIED, true)));
        assertThatThrownBy(() -> service.closeExpense(E_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Finance Manager approval");
    }

    @Test
    void closeExpense_fromApproved_whenOverLimit_closes() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.APPROVED, true);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(ORG, E_ID)).thenReturn(java.util.List.of());

        service.closeExpense(E_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.CLOSED);
    }

    // ---------------------------------------------------- finance manager

    @Test
    void approveReceipt_movesVerifiedToApproved() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.VERIFIED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.approveReceipt(R_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.APPROVED), eq(ACTOR_ID), any());
    }

    @Test
    void approveReceipt_conflict_whenNotVerified() {
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(receiveDoc(WorkflowStatus.SUBMITTED)));
        assertThatThrownBy(() -> service.approveReceipt(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("SUBMITTED");
    }

    /**
     * rev 49: the FM's query on a VERIFIED receipt goes to FM_QUERIED, not QUERIED — it routes
     * back to the Accountant's own queue, not the Cashier. The Accountant's own query on a
     * fresh SUBMITTED receipt (queryReceipt_asAccountant_...) is unchanged, still QUERIED.
     */
    @Test
    void queryReceipt_asFinanceManager_movesVerifiedToFmQueried() {
        when(branchScope.currentRole()).thenReturn(Role.FINANCE_MANAGER);
        ReceiveDocument doc = receiveDoc(WorkflowStatus.VERIFIED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.queryReceipt(R_ID, "Bank ref looks wrong");

        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.FM_QUERIED);
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.QUERIED), eq(ACTOR_ID), any());
    }

    @Test
    void resubmitReceiptToFm_movesFmQueriedToVerified_andAudits() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.FM_QUERIED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.resubmitReceiptToFm(R_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.VERIFIED);
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.VERIFIED), eq(ACTOR_ID), any());
    }

    @Test
    void resubmitReceiptToFm_conflict_whenNotFmQueried() {
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(receiveDoc(WorkflowStatus.SUBMITTED)));
        assertThatThrownBy(() -> service.resubmitReceiptToFm(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("SUBMITTED");
        verify(receiveDocumentRepo, never()).save(any());
    }

    /** rev 49: the override gate widens to allow fixing an FM-queried receipt, not just a fresh one. */
    @Test
    void overrideReceiptLine_allowedWhileFmQueried() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.FM_QUERIED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdAndLineNo(ORG, R_ID, 1))
            .thenReturn(Optional.of(settlementLine(1, new BigDecimal("500"))));

        service.overrideReceiptLine(R_ID, 1, new BigDecimal("450"), "Bank charge deducted");

        verify(settlementLineRepo).save(any());
    }

    @Test
    void queryExpense_asFinanceManager_movesVerifiedToFmQueried() {
        when(branchScope.currentRole()).thenReturn(Role.FINANCE_MANAGER);
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.VERIFIED, false);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));

        service.queryExpense(E_ID, "Receipt attachment is missing");

        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.FM_QUERIED);
    }

    @Test
    void resubmitExpenseToFm_movesFmQueriedToVerified() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.FM_QUERIED, false);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));

        service.resubmitExpenseToFm(E_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.VERIFIED);
    }

    @Test
    void queryCash_asFinanceManager_movesVerifiedToFmQueried() {
        when(branchScope.currentRole()).thenReturn(Role.FINANCE_MANAGER);
        com.dams.cash.entity.CashDocument doc = cashDoc(com.dams.cash.entity.CashWorkflowStatus.VERIFIED);
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG)).thenReturn(Optional.of(doc));

        service.queryCash(C_ID, "Direction looks backwards");

        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.FM_QUERIED);
    }

    @Test
    void resubmitCashToFm_movesFmQueriedToVerified() {
        com.dams.cash.entity.CashDocument doc = cashDoc(com.dams.cash.entity.CashWorkflowStatus.FM_QUERIED);
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG)).thenReturn(Optional.of(doc));

        service.resubmitCashToFm(C_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.VERIFIED);
    }

    @Test
    void rejectExpense_asFinanceManager_movesVerifiedToRejected() {
        when(branchScope.currentRole()).thenReturn(Role.FINANCE_MANAGER);
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.VERIFIED, false);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));

        service.rejectExpense(E_ID, "Not a valid business expense");

        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.REJECTED);
    }

    @Test
    void approveExpense_movesVerifiedToApproved() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.VERIFIED, true);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));

        service.approveExpense(E_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.APPROVED);
        verify(auditService).recordUserEvent(eq("ExpenseDocument"), eq(E_ID), eq(BRANCH),
            eq(EventType.APPROVED), eq(ACTOR_ID), any());
    }

    // ---------------------------------------------------- cash

    @Test
    void verifyCash_movesSubmittedToVerified_andAudits() {
        com.dams.cash.entity.CashDocument doc = cashDoc(com.dams.cash.entity.CashWorkflowStatus.SUBMITTED);
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG)).thenReturn(Optional.of(doc));

        service.verifyCash(C_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.VERIFIED);
        verify(auditService).recordUserEvent(eq("CashDocument"), eq(C_ID), eq(BRANCH),
            eq(EventType.VERIFIED), eq(ACTOR_ID), any());
        verify(cashDocumentService).get(C_ID);
    }

    @Test
    void verifyCash_conflict_whenNotSubmitted() {
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG))
            .thenReturn(Optional.of(cashDoc(com.dams.cash.entity.CashWorkflowStatus.VERIFIED)));
        assertThatThrownBy(() -> service.verifyCash(C_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("VERIFIED");
    }

    @Test
    void approveCash_movesVerifiedToApproved() {
        com.dams.cash.entity.CashDocument doc = cashDoc(com.dams.cash.entity.CashWorkflowStatus.VERIFIED);
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG)).thenReturn(Optional.of(doc));

        service.approveCash(C_ID);

        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.APPROVED);
        verify(auditService).recordUserEvent(eq("CashDocument"), eq(C_ID), eq(BRANCH),
            eq(EventType.APPROVED), eq(ACTOR_ID), any());
    }

    @Test
    void queryCash_asAccountant_movesSubmittedToQueried_withNote() {
        com.dams.cash.entity.CashDocument doc = cashDoc(com.dams.cash.entity.CashWorkflowStatus.SUBMITTED);
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG)).thenReturn(Optional.of(doc));

        service.queryCash(C_ID, "Bank reference is missing");

        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.QUERIED);
        ArgumentCaptor<Map<String, Object>> detail = captor();
        verify(auditService).recordUserEvent(eq("CashDocument"), eq(C_ID), eq(BRANCH),
            eq(EventType.QUERIED), eq(ACTOR_ID), detail.capture());
        assertThat(detail.getValue()).containsEntry("note", "Bank reference is missing");
    }

    // ---------------------------------------------------- queue

    @Test
    void receiptQueue_returnsEmpty_whenTheAccountantHasNoBranches() {
        when(branchScope.allowedBranchIds()).thenReturn(Optional.of(java.util.Set.of()));
        assertThat(service.receiptQueue()).isEmpty();
        verify(receiveDocumentRepo, never())
            .findByOrgIdAndWorkflowStatusInAndBranchIdInOrderBySubmittedAtAscIdAsc(any(), any(), any());
    }

    /** rev 49: the live queue is SUBMITTED and FM_QUERIED together — both need the Accountant. */
    @Test
    void receiptQueue_includesBothSubmittedAndFmQueried() {
        when(branchScope.allowedBranchIds()).thenReturn(Optional.of(java.util.Set.of(BRANCH)));
        ReceiveDocument submitted = receiveDoc(WorkflowStatus.SUBMITTED);
        ReceiveDocument fmQueried = receiveDoc(WorkflowStatus.FM_QUERIED);
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInAndBranchIdInOrderBySubmittedAtAscIdAsc(
            eq(ORG), eq(java.util.List.of(WorkflowStatus.SUBMITTED, WorkflowStatus.FM_QUERIED)), eq(java.util.Set.of(BRANCH))))
            .thenReturn(java.util.List.of(submitted, fmQueried));

        assertThat(service.receiptQueue()).hasSize(2);
    }

    @Test
    void verifiedReceiptQueue_returnsDocsWithTheirRealWorkflowStatus() {
        when(branchScope.allowedBranchIds()).thenReturn(Optional.of(java.util.Set.of(BRANCH)));
        ReceiveDocument verified = receiveDoc(WorkflowStatus.VERIFIED);
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInAndBranchIdInOrderBySubmittedAtDescIdDesc(
            eq(ORG), eq(java.util.List.of(WorkflowStatus.VERIFIED, WorkflowStatus.APPROVED)), eq(java.util.Set.of(BRANCH))))
            .thenReturn(java.util.List.of(verified));

        var result = service.verifiedReceiptQueue();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workflowStatus()).isEqualTo("VERIFIED");
        assertThat(result.get(0).id()).isEqualTo(R_ID);
    }

    /**
     * rev 40: a claim is an open claim from the moment its receipt enters the workflow — it must
     * not sit indistinguishable among ordinary receipts in "awaiting final approval" just because
     * the FM has not approved it yet. (Closing still requires APPROVED — ClaimCloseService.)
     */
    @Test
    void fmReceiptQueue_listsAVerifiedClaimAsAnOpenClaim_notOnlyApprovedOnes() {
        ReceiveDocument verifiedClaim = receiveDoc(WorkflowStatus.VERIFIED);
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(ORG, WorkflowStatus.VERIFIED))
            .thenReturn(java.util.List.of(verifiedClaim));
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInOrderBySubmittedAtAscIdAsc(
            eq(ORG), eq(java.util.List.of(WorkflowStatus.SUBMITTED, WorkflowStatus.QUERIED, WorkflowStatus.FM_QUERIED,
                WorkflowStatus.VERIFIED, WorkflowStatus.APPROVED))))
            .thenReturn(java.util.List.of(verifiedClaim));
        when(jobCardRepo.findByOrgIdAndIdIn(ORG, java.util.List.of(11L))).thenReturn(java.util.List.of(claimJobCard()));

        var queue = service.fmReceiptQueue();

        assertThat(queue.openClaims()).extracting(ReviewQueueItem::id).containsExactly(R_ID);
        assertThat(queue.openClaims().get(0).workflowStatus()).isEqualTo("VERIFIED");
        // still needs approval too — it belongs in both lists, for two different actions
        assertThat(queue.awaitingApproval()).extracting(ReviewQueueItem::id).containsExactly(R_ID);
    }

    @Test
    void fmReceiptQueue_excludesAClaimWhoseJobCardIsAlreadyClosed() {
        ReceiveDocument approvedClaim = receiveDoc(WorkflowStatus.APPROVED);
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInOrderBySubmittedAtAscIdAsc(eq(ORG), any()))
            .thenReturn(java.util.List.of(approvedClaim));
        when(jobCardRepo.findByOrgIdAndIdIn(ORG, java.util.List.of(11L))).thenReturn(java.util.List.of(claimJobCard()));
        when(claimCloseRepo.findJobCardIdsByOrgId(ORG)).thenReturn(java.util.List.of(11L));

        assertThat(service.fmReceiptQueue().openClaims()).isEmpty();
    }

    // ---------------------------------------------------- fixtures

    private static AppUser actor(Role role) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", ACTOR_ID);
        u.setRole(role);
        return u;
    }

    /** The job card behind {@link #receiveDoc}, carrying a claim type (AMC). */
    private static JobCard claimJobCard() {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", 11L);
        jc.setOrgId(ORG);
        jc.setBranchId(BRANCH);
        jc.setCustomerId(21L);
        jc.setCategoryId(5L);
        jc.setBusinessStatusId(6L);
        jc.setClaimTypeId(4L);
        return jc;
    }

    /** Same job card, minus the claim type — the direct-approve tests' eligible case. */
    private static JobCard plainJobCard() {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", 11L);
        jc.setOrgId(ORG);
        jc.setBranchId(BRANCH);
        jc.setCustomerId(21L);
        jc.setCategoryId(5L);
        jc.setBusinessStatusId(6L);
        return jc;
    }

    private static Organization organization(boolean directApproveOn) {
        Organization o = new Organization("Test Org");
        ReflectionTestUtils.setField(o, "id", ORG);
        o.setAccountantDirectApproveCash(directApproveOn);
        return o;
    }

    private static ReceiveBusinessStatus creditStatus() {
        ReceiveBusinessStatus s = new ReceiveBusinessStatus();
        ReflectionTestUtils.setField(s, "id", 99L);
        s.setOrgId(ORG);
        s.setName("Credit");
        return s;
    }

    private static SettlementMode cashMode() {
        SettlementMode m = new SettlementMode();
        ReflectionTestUtils.setField(m, "id", 1L);
        m.setOrgId(ORG);
        m.setName("Cash");
        m.setCash(true);
        return m;
    }

    private static ReceiveDocument receiveDoc(WorkflowStatus status) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", R_ID);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH);
        d.setJobCardId(11L);
        d.setDocumentNo("OOR-AUG26-R-005");
        d.setWorkflowStatus(status);
        d.setCreatedBy(7L);
        d.setLastModifiedBy(7L);
        return d;
    }

    private static SettlementLine settlementLine(int lineNo, BigDecimal amount) {
        SettlementLine l = new SettlementLine();
        ReflectionTestUtils.setField(l, "id", 900L + lineNo);
        l.setOrgId(ORG);
        l.setReceiveDocumentId(R_ID);
        l.setLineNo(lineNo);
        l.setLineId("OOR-AUG26-R-005-L" + lineNo);
        l.setTransactionDate(LocalDate.of(2026, 8, 20));
        l.setSettlementModeId(1L);
        l.setAmount(amount);
        l.setCreatedBy(7L);
        l.setCreatedAt(Instant.now());
        return l;
    }

    private static ExpenseDocument expenseDoc(ExpenseWorkflowStatus status, boolean overLimit) {
        ExpenseDocument d = new ExpenseDocument();
        ReflectionTestUtils.setField(d, "id", E_ID);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH);
        d.setReceiverId(21L);
        d.setExpenseCategoryId(31L);
        d.setBusinessStatusId(41L);
        d.setDocumentNo("OOR-AUG26-E-005");
        d.setWorkflowStatus(status);
        d.setOverLimit(overLimit);
        d.setCreatedBy(7L);
        d.setLastModifiedBy(7L);
        return d;
    }

    private static ExpenseLine expenseLine(int lineNo, BigDecimal amount) {
        ExpenseLine l = new ExpenseLine();
        ReflectionTestUtils.setField(l, "id", 700L + lineNo);
        l.setOrgId(ORG);
        l.setExpenseDocumentId(E_ID);
        l.setLineNo(lineNo);
        l.setLineId("OOR-AUG26-E-005-L" + lineNo);
        l.setTransactionDate(LocalDate.of(2026, 8, 20));
        l.setSubCategoryId(1L);
        l.setExpenseModeId(1L);
        l.setAmount(amount);
        l.setCreatedBy(7L);
        l.setCreatedAt(Instant.now());
        return l;
    }

    private static com.dams.cash.entity.CashDocument cashDoc(com.dams.cash.entity.CashWorkflowStatus status) {
        com.dams.cash.entity.CashDocument d = new com.dams.cash.entity.CashDocument();
        ReflectionTestUtils.setField(d, "id", C_ID);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH);
        d.setDocumentNo("OOR-AUG26-C-005");
        d.setDirection(com.dams.cash.entity.CashDirection.IN);
        d.setTransactionDate(LocalDate.of(2026, 8, 20));
        d.setAmount(new BigDecimal("5000"));
        d.setWorkflowStatus(status);
        d.setCreatedBy(7L);
        d.setLastModifiedBy(7L);
        return d;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> captor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
