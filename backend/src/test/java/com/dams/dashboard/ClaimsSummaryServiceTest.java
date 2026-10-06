package com.dams.dashboard;

import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.dashboard.dto.ClaimsSummary;
import com.dams.dashboard.service.ClaimsSummaryService;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.entity.ExpenseBusinessStatus;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The claims summary: claimed = received + rejected + pending, expense and receipt claims
 * counted together and on their own, each claim in the period it was raised.
 */
@ExtendWith(MockitoExtension.class)
class ClaimsSummaryServiceTest {

    private static final long ORG = 1L;
    private static final long CLAIM_STATUS = 41L;

    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private ExpenseBusinessStatusRepository expenseStatusRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private BranchRepository branchRepo;

    private ClaimsSummaryService service;

    @BeforeEach
    void setUp() {
        service = new ClaimsSummaryService(expenseDocumentRepo, expenseLineRepo, expenseStatusRepo,
            receiveDocumentRepo, jobCardRepo, settlementLineRepo, claimCloseRepo, branchRepo);
        TenantContext.setOrgId(ORG);
        ExpenseBusinessStatus claimStatus = new ExpenseBusinessStatus();
        ReflectionTestUtils.setField(claimStatus, "id", CLAIM_STATUS);
        lenient().when(expenseStatusRepo.findByOrgIdAndTriggersClaimTrue(ORG)).thenReturn(List.of(claimStatus));
        lenient().when(expenseDocumentRepo.findClaimExpenses(eq(ORG), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(jobCardRepo.findByOrgId(ORG)).thenReturn(List.of());
        lenient().when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInOrderBySubmittedAtAscIdAsc(eq(ORG), any()))
            .thenReturn(List.of());
        lenient().when(claimCloseRepo.findByOrgId(ORG)).thenReturn(List.of());
        lenient().when(settlementLineRepo.sumAmountByJobCard(ORG)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void noClaims_isAllZero() {
        ClaimsSummary s = service.summary(null, "mtd");

        assertThat(s.total().count()).isZero();
        assertThat(s.total().claimed()).isEqualByComparingTo("0");
    }

    @Test
    void expenseClaims_closedShortPaidIsRejected_openIsPending_legacyClosedIsLeftOut() {
        ExpenseDocument closed = claimExpense(10L, ExpenseWorkflowStatus.CLOSED);
        closed.setClaimFinalAmount(new BigDecimal("4200"));
        closed.setClaimComputedTotal(new BigDecimal("5000"));
        ExpenseDocument open = claimExpense(11L, ExpenseWorkflowStatus.VERIFIED);
        ExpenseDocument legacy = claimExpense(12L, ExpenseWorkflowStatus.CLOSED);   // closed before claims had a Finance step
        when(expenseDocumentRepo.findClaimExpenses(eq(ORG), isNull(), any(), any(), any()))
            .thenReturn(List.of(closed, open, legacy));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(eq(ORG), any()))
            .thenReturn(List.of(line(10L, "5000"), line(11L, "3000"), line(12L, "999")));

        ClaimsSummary s = service.summary(null, "mtd");

        assertThat(s.expenses().count()).isEqualTo(2);
        assertThat(s.expenses().open()).isEqualTo(1);
        assertThat(s.expenses().claimed()).isEqualByComparingTo("8000");
        assertThat(s.expenses().received()).isEqualByComparingTo("4200");
        assertThat(s.expenses().rejected()).isEqualByComparingTo("800");
        assertThat(s.expenses().pending()).isEqualByComparingTo("3000");
    }

    @Test
    void aClaimFullyWrittenOff_isAllRejected() {
        ExpenseDocument closed = claimExpense(10L, ExpenseWorkflowStatus.CLOSED);
        closed.setClaimFinalAmount(BigDecimal.ZERO);
        closed.setClaimComputedTotal(new BigDecimal("5000"));
        when(expenseDocumentRepo.findClaimExpenses(eq(ORG), isNull(), any(), any(), any())).thenReturn(List.of(closed));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(eq(ORG), any()))
            .thenReturn(List.of(line(10L, "5000")));

        ClaimsSummary s = service.summary(null, "mtd");

        assertThat(s.expenses().received()).isEqualByComparingTo("0");
        assertThat(s.expenses().rejected()).isEqualByComparingTo("5000");
    }

    @Test
    void receiptClaims_closedUsesTheFinalAmount_openUsesPaymentsSoFar() {
        JobCard closedJc = claimJobCard(70L, "10000");
        JobCard openJc = claimJobCard(71L, "6000");
        when(jobCardRepo.findByOrgId(ORG)).thenReturn(List.of(closedJc, openJc));
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInOrderBySubmittedAtAscIdAsc(eq(ORG), any()))
            .thenReturn(List.of(receipt(70L, Instant.now()), receipt(71L, Instant.now())));
        ClaimClose close = new ClaimClose();
        close.setJobCardId(70L);
        close.setFinalAmount(new BigDecimal("8000"));
        when(claimCloseRepo.findByOrgId(ORG)).thenReturn(List.of(close));
        when(settlementLineRepo.sumAmountByJobCard(ORG)).thenReturn(List.<Object[]>of(
            new Object[]{70L, new BigDecimal("9000")}, new Object[]{71L, new BigDecimal("2500")}));

        ClaimsSummary s = service.summary(null, "mtd");

        assertThat(s.receipts().count()).isEqualTo(2);
        assertThat(s.receipts().open()).isEqualTo(1);
        assertThat(s.receipts().claimed()).isEqualByComparingTo("16000");
        assertThat(s.receipts().received()).isEqualByComparingTo("10500");   // 8000 final + 2500 so far
        assertThat(s.receipts().rejected()).isEqualByComparingTo("2000");     // 10000 claimed, 8000 final
        assertThat(s.receipts().pending()).isEqualByComparingTo("3500");      // 6000 claimed, 2500 so far
    }

    @Test
    void aReceiptClaimRaisedBeforeThePeriod_isNotCounted() {
        JobCard jc = claimJobCard(70L, "10000");
        when(jobCardRepo.findByOrgId(ORG)).thenReturn(List.of(jc));
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInOrderBySubmittedAtAscIdAsc(eq(ORG), any()))
            .thenReturn(List.of(receipt(70L, Instant.now().minus(Duration.ofDays(90)))));

        ClaimsSummary s = service.summary(null, "mtd");

        assertThat(s.receipts().count()).isZero();
    }

    @Test
    void totalIsTheTwoKindsAdded_andClaimedEqualsReceivedPlusRejectedPlusPending() {
        ExpenseDocument closed = claimExpense(10L, ExpenseWorkflowStatus.CLOSED);
        closed.setClaimFinalAmount(new BigDecimal("4200"));
        closed.setClaimComputedTotal(new BigDecimal("5000"));
        when(expenseDocumentRepo.findClaimExpenses(eq(ORG), isNull(), any(), any(), any())).thenReturn(List.of(closed));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(eq(ORG), any()))
            .thenReturn(List.of(line(10L, "5000")));
        JobCard openJc = claimJobCard(71L, "6000");
        when(jobCardRepo.findByOrgId(ORG)).thenReturn(List.of(openJc));
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusInOrderBySubmittedAtAscIdAsc(eq(ORG), any()))
            .thenReturn(List.of(receipt(71L, Instant.now())));
        when(settlementLineRepo.sumAmountByJobCard(ORG)).thenReturn(List.<Object[]>of(new Object[]{71L, new BigDecimal("2500")}));

        ClaimsSummary s = service.summary(null, "mtd");

        assertThat(s.total().count()).isEqualTo(2);
        assertThat(s.total().claimed()).isEqualByComparingTo("11000");
        assertThat(s.total().received()).isEqualByComparingTo("6700");
        assertThat(s.total().rejected()).isEqualByComparingTo("800");
        assertThat(s.total().pending()).isEqualByComparingTo("3500");
        assertThat(s.total().received().add(s.total().rejected()).add(s.total().pending()))
            .isEqualByComparingTo(s.total().claimed());
    }

    @Test
    void anUnknownBranch_isRefused() {
        when(branchRepo.findByIdAndOrgId(99L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summary(99L, "mtd")).isInstanceOf(DamsException.class);
    }

    private static ExpenseDocument claimExpense(long id, ExpenseWorkflowStatus status) {
        ExpenseDocument d = new ExpenseDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setBranchId(3L);
        d.setBusinessStatusId(CLAIM_STATUS);
        d.setWorkflowStatus(status);
        d.setSubmittedAt(Instant.now());
        return d;
    }

    private static ExpenseLine line(long docId, String amount) {
        ExpenseLine l = new ExpenseLine();
        l.setExpenseDocumentId(docId);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    private static JobCard claimJobCard(long id, String invoice) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", id);
        jc.setOrgId(ORG);
        jc.setBranchId(3L);
        jc.setClaimTypeId(5L);
        jc.setInvoiceAmount(new BigDecimal(invoice));
        return jc;
    }

    private static ReceiveDocument receipt(long jobCardId, Instant submittedAt) {
        ReceiveDocument d = new ReceiveDocument();
        d.setJobCardId(jobCardId);
        d.setWorkflowStatus(WorkflowStatus.APPROVED);
        d.setSubmittedAt(submittedAt);
        return d;
    }
}
