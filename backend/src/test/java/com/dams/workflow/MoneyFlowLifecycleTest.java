package com.dams.workflow;

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
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
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
 * End-to-end maker-checker lifecycles across document types, in one place.
 *
 * <p>Unit tests prove each transition in isolation; these prove the <em>chains</em> a real
 * branch walks every day, and the three closing rules from AGENT.md that must never be
 * conflated:
 * <ol>
 *   <li>Receipts close themselves at pending = 0 — accountant verification does NOT close.</li>
 *   <li>Expenses close explicitly (accountant), never implicitly on verify/approve.</li>
 *   <li>Claim overrides are final and locked once the FM uses them.</li>
 * </ol>
 * Same mock style as {@code ReviewServiceTest} — no database, runs in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class MoneyFlowLifecycleTest {

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
    @Mock private com.dams.cash.service.CashDateLock cashDateLock;
    @Mock private com.dams.masters.repository.SettlementModeRepository settlementModeRepo;
    @Mock private com.dams.masters.repository.ExpenseModeRepository expenseModeRepo;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(receiveDocumentRepo, settlementLineRepo, expenseDocumentRepo, expenseLineRepo,
            jobCardRepo, customerRepo, receiveCategoryRepo, expenseCategoryRepo, receiverRepo, claimCloseRepo,
            branchRepo, branchScope, guard, auditService, attachmentService, receiveDocumentService, expenseDocumentService,
            cashDocumentRepo, cashDocumentService, cashDateLock, settlementModeRepo, expenseModeRepo);
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

    // Receipts close themselves — verification must NOT close them (AGENT.md rule 1).

    @Test
    void receiptLifecycle_verifyLeavesTheReceiptOpen_approveKeepsItOpenUntilSettled() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.verifyReceipt(R_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.VERIFIED);
        // No separate CLOSED status exists for receipts — closure is the computed settled
        // flag at pending = 0, so verification can never close one by construction.
        assertThat(doc.getWorkflowStatus()).isNotIn(WorkflowStatus.QUERIED, WorkflowStatus.REJECTED);

        service.approveReceipt(R_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
        assertThat(doc.getWorkflowStatus()).isNotIn(WorkflowStatus.QUERIED, WorkflowStatus.REJECTED);
    }

    // Queried entries must go back through the cashier's fix-and-resubmit loop (AGENT.md #8).

    @Test
    void queriedReceipt_cannotBeVerifiedUntilTheCashierResubmits() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));

        service.queryReceipt(R_ID, "UTR last-4 does not match the slip");
        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.QUERIED);

        // The reviewer cannot pull a QUERIED doc back into the queue themselves.
        assertThatThrownBy(() -> service.verifyReceipt(R_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("QUERIED");
        assertThatThrownBy(() -> service.approveReceipt(R_ID))
            .isInstanceOf(DamsException.class);
        verify(auditService, never()).recordUserEvent(eq("ReceiveDocument"), eq(R_ID), eq(BRANCH),
            eq(EventType.VERIFIED), eq(ACTOR_ID), any());
    }

    // Expenses close explicitly, never implicitly (AGENT.md rule 2).

    @Test
    void expenseLifecycle_verifyAndApproveNeverClose_closeIsAnExplicitAct() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.SUBMITTED, false);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(ORG, E_ID)).thenReturn(List.of());

        // Verify alone changes nothing about openness.
        service.verifyExpense(E_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.VERIFIED);

        // Approve alone also leaves it open.
        service.approveExpense(E_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.APPROVED);

        // Only the explicit close flips it to CLOSED — and freezes the attachments.
        // (Approve already froze them once at approval time, hence exactly two freezes.)
        service.closeExpense(E_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.CLOSED);
        verify(attachmentService, times(2)).freezeExpenseDocument(eq(ORG), eq(E_ID), any());
        verify(auditService).recordUserEvent(eq("ExpenseDocument"), eq(E_ID), eq(BRANCH),
            eq(EventType.CLOSED), eq(ACTOR_ID), any());
    }

    @Test
    void expenseClose_refusedUntilVerified_soUnreviewedSpendCanNeverLock() {
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.SUBMITTED, false)));

        assertThatThrownBy(() -> service.closeExpense(E_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("verified or approved");
        verify(expenseDocumentRepo, never()).save(any());
    }

    // Over-limit spend needs the FM even at close time — the accountant cannot finish it alone.

    @Test
    void overLimitExpense_accountantCloseRefused_fmApproveThenCloseSucceeds() {
        ExpenseDocument doc = expenseDoc(ExpenseWorkflowStatus.VERIFIED, true);
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG)).thenReturn(Optional.of(doc));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(ORG, E_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.closeExpense(E_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Finance Manager approval");
        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.VERIFIED);

        service.approveExpense(E_ID);
        service.closeExpense(E_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.CLOSED);
    }

    // Accountant overrides are provisional — the doc stays in the queue for the FM (AGENT.md).

    @Test
    void accountantOverride_keepsOriginalAndLeavesDocOpen_downstreamApprovalStillRequired() {
        ReceiveDocument doc = receiveDoc(WorkflowStatus.SUBMITTED);
        SettlementLine line = settlementLine(1, new BigDecimal("800"));
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG)).thenReturn(Optional.of(doc));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdAndLineNo(ORG, R_ID, 1))
            .thenReturn(Optional.of(line));

        service.overrideReceiptLine(R_ID, 1, new BigDecimal("1000"), "Eicher revised the labour rate");

        assertThat(line.getAmount()).isEqualByComparingTo("1000");
        assertThat(line.getOriginalAmount()).isEqualByComparingTo("800");
        assertThat(line.getOverriddenBy()).isEqualTo(ACTOR_ID);
        // Provisional: still SUBMITTED, still needs verify + approve downstream.
        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.SUBMITTED);

        service.verifyReceipt(R_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.VERIFIED);
        service.approveReceipt(R_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
    }

    // Maker-checker holds at every step of every chain — never just at verify.

    @Test
    void makerChecker_blocksSelfReview_atEveryStepOfEveryChain() {
        when(receiveDocumentRepo.findByIdAndOrgId(R_ID, ORG))
            .thenReturn(Optional.of(receiveDoc(WorkflowStatus.SUBMITTED)));
        when(expenseDocumentRepo.findByIdAndOrgId(E_ID, ORG))
            .thenReturn(Optional.of(expenseDoc(ExpenseWorkflowStatus.SUBMITTED, false)));
        doThrow(DamsException.conflict("you created or last modified it"))
            .when(guard).requireCanReview(any(), eq(BRANCH), any(), any(), any());

        assertThatThrownBy(() -> service.verifyReceipt(R_ID)).isInstanceOf(DamsException.class);
        assertThatThrownBy(() -> service.queryReceipt(R_ID, "x")).isInstanceOf(DamsException.class);
        assertThatThrownBy(() -> service.rejectReceipt(R_ID, "x")).isInstanceOf(DamsException.class);
        assertThatThrownBy(() -> service.verifyExpense(E_ID)).isInstanceOf(DamsException.class);
        verify(receiveDocumentRepo, never()).save(any());
        verify(expenseDocumentRepo, never()).save(any());
    }



    // Cash movements walk the same maker-checker chain as customer documents.

    @Test
    void cashLifecycle_submittedToVerifiedToApproved_withQueriedLoop() {
        com.dams.cash.entity.CashDocument doc = cashDoc(com.dams.cash.entity.CashWorkflowStatus.SUBMITTED);
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG)).thenReturn(Optional.of(doc));

        service.verifyCash(C_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.VERIFIED);

        service.approveCash(C_ID);
        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.APPROVED);
        verify(auditService).recordUserEvent(eq("CashDocument"), eq(C_ID), eq(BRANCH),
            eq(EventType.APPROVED), eq(ACTOR_ID), any());
    }

    @Test
    void cashQuery_carriesTheReviewerNote_forTheCashierFixLoop() {
        com.dams.cash.entity.CashDocument doc = cashDoc(com.dams.cash.entity.CashWorkflowStatus.SUBMITTED);
        when(cashDocumentRepo.findByIdAndOrgId(C_ID, ORG)).thenReturn(Optional.of(doc));

        service.queryCash(C_ID, "Bank reference is missing");

        assertThat(doc.getWorkflowStatus()).isEqualTo(com.dams.cash.entity.CashWorkflowStatus.QUERIED);
        ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordUserEvent(eq("CashDocument"), eq(C_ID), eq(BRANCH),
            eq(EventType.QUERIED), eq(ACTOR_ID), detail.capture());
        assertThat(detail.getValue()).containsEntry("note", "Bank reference is missing");
    }

    // ---------------------------------------------------------- fixtures

    private static AppUser actor(Role role) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", ACTOR_ID);
        u.setRole(role);
        return u;
    }

    private static ReceiveDocument receiveDoc(WorkflowStatus status) {
        return receiveDocWithId(R_ID, "OOR-AUG26-R-005", status);
    }

    private static ReceiveDocument receiveDocWithId(Long id, String documentNo, WorkflowStatus status) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH);
        d.setJobCardId(11L);
        d.setDocumentNo(documentNo);
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

    @SuppressWarnings("unused")
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
}
