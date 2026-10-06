package com.dams.dashboard.service;

import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.dashboard.dto.ClaimsSummary;
import com.dams.dashboard.dto.ClaimsSummary.ClaimTotals;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The claims summary (rev 62) shown on the Owner dashboard and the Finance Manager page: what was
 * claimed, what came back, what was rejected, what is still open — expense claims (Transfer to
 * Claim) and receipt claims (warranty / AMC / CG) together, and each on its own.
 *
 * A claim belongs to the period in which it was <b>raised</b> (an expense's submission; a receipt
 * claim's first live receive document), so changing the period changes the totals consistently
 * for both kinds. Read-only.
 */
@Service
public class ClaimsSummaryService {

    /** Receive-document states that mean the claim has been raised and is still alive. */
    private static final List<WorkflowStatus> LIVE_RECEIPT_STATES = List.of(
        WorkflowStatus.SUBMITTED, WorkflowStatus.QUERIED, WorkflowStatus.FM_QUERIED,
        WorkflowStatus.VERIFIED, WorkflowStatus.APPROVED);

    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final ExpenseBusinessStatusRepository expenseStatusRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final JobCardRepository jobCardRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final BranchRepository branchRepo;

    public ClaimsSummaryService(ExpenseDocumentRepository expenseDocumentRepo,
                                ExpenseLineRepository expenseLineRepo,
                                ExpenseBusinessStatusRepository expenseStatusRepo,
                                ReceiveDocumentRepository receiveDocumentRepo,
                                JobCardRepository jobCardRepo,
                                SettlementLineRepository settlementLineRepo,
                                ClaimCloseRepository claimCloseRepo,
                                BranchRepository branchRepo) {
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.expenseStatusRepo = expenseStatusRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.jobCardRepo = jobCardRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.branchRepo = branchRepo;
    }

    @Transactional(readOnly = true)
    public ClaimsSummary summary(Long branchId, String period) {
        Long orgId = TenantContext.requireOrgId();
        if (branchId != null && branchRepo.findByIdAndOrgId(branchId, orgId).isEmpty()) {
            throw DamsException.notFound("Branch", branchId);
        }
        LocalDate today = OrgTime.today();
        LocalDate fromDate = "today".equals(period) ? today : today.withDayOfMonth(1);
        Instant from = fromDate.atStartOfDay(OrgTime.ZONE).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(OrgTime.ZONE).toInstant();

        ClaimTotals expenses = expenseClaims(orgId, branchId, from, to);
        ClaimTotals receipts = receiptClaims(orgId, branchId, from, to);
        return new ClaimsSummary("today".equals(period) ? "today" : "mtd", expenses.plus(receipts), expenses, receipts);
    }

    // ---- expenses marked Transfer to Claim ----

    private ClaimTotals expenseClaims(Long orgId, Long branchId, Instant from, Instant to) {
        List<Long> claimStatusIds = expenseStatusRepo.findByOrgIdAndTriggersClaimTrue(orgId).stream()
            .map(ExpenseBusinessStatus::getId).toList();
        List<ExpenseDocument> docs = expenseDocumentRepo.findClaimExpenses(orgId, branchId, from, to,
            claimStatusIds.isEmpty() ? List.of(-1L) : claimStatusIds);
        if (docs.isEmpty()) {
            return ClaimTotals.empty();
        }
        Map<Long, BigDecimal> totals = expenseLineRepo
            .findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(orgId, docs.stream().map(ExpenseDocument::getId).toList())
            .stream().collect(Collectors.groupingBy(ExpenseLine::getExpenseDocumentId,
                Collectors.reducing(BigDecimal.ZERO, ExpenseLine::getAmount, BigDecimal::add)));

        ClaimTotals t = ClaimTotals.empty();
        for (ExpenseDocument d : docs) {
            boolean closedAsClaim = d.getClaimFinalAmount() != null;
            if (d.getWorkflowStatus() == ExpenseWorkflowStatus.CLOSED && !closedAsClaim) {
                continue;   // closed by the Accountant before claims had a Finance step: no recovery recorded
            }
            BigDecimal claimed = closedAsClaim && d.getClaimComputedTotal() != null
                ? d.getClaimComputedTotal() : totals.getOrDefault(d.getId(), BigDecimal.ZERO);
            t = t.plus(one(closedAsClaim, claimed, closedAsClaim ? d.getClaimFinalAmount() : BigDecimal.ZERO));
        }
        return t;
    }

    // ---- warranty / AMC / CG receipts ----

    private ClaimTotals receiptClaims(Long orgId, Long branchId, Instant from, Instant to) {
        Map<Long, JobCard> claimJobCards = jobCardRepo.findByOrgId(orgId).stream()
            .filter(jc -> jc.getClaimTypeId() != null)
            .filter(jc -> branchId == null || branchId.equals(jc.getBranchId()))
            .collect(Collectors.toMap(JobCard::getId, jc -> jc, (a, b) -> a));
        if (claimJobCards.isEmpty()) {
            return ClaimTotals.empty();
        }
        // First live submission per job card, oldest first — when the claim was raised.
        Map<Long, Instant> raisedAt = new HashMap<>();
        for (ReceiveDocument d : receiveDocumentRepo.findByOrgIdAndWorkflowStatusInOrderBySubmittedAtAscIdAsc(orgId, LIVE_RECEIPT_STATES)) {
            if (d.getSubmittedAt() != null && claimJobCards.containsKey(d.getJobCardId())) {
                raisedAt.putIfAbsent(d.getJobCardId(), d.getSubmittedAt());
            }
        }
        Map<Long, BigDecimal> finalByJobCard = claimCloseRepo.findByOrgId(orgId).stream()
            .collect(Collectors.toMap(ClaimClose::getJobCardId, ClaimClose::getFinalAmount, (a, b) -> a));
        Map<Long, BigDecimal> receivedByJobCard = new HashMap<>();
        for (Object[] r : settlementLineRepo.sumAmountByJobCard(orgId)) {
            receivedByJobCard.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }

        ClaimTotals t = ClaimTotals.empty();
        for (Map.Entry<Long, Instant> e : raisedAt.entrySet()) {
            if (e.getValue().isBefore(from) || !e.getValue().isBefore(to)) {
                continue;
            }
            JobCard jc = claimJobCards.get(e.getKey());
            BigDecimal paidSoFar = receivedByJobCard.getOrDefault(jc.getId(), BigDecimal.ZERO);
            BigDecimal claimed = jc.getInvoiceAmount() != null ? jc.getInvoiceAmount() : paidSoFar;
            BigDecimal finalAmount = finalByJobCard.get(jc.getId());
            t = t.plus(finalAmount != null ? one(true, claimed, finalAmount) : one(false, claimed, paidSoFar));
        }
        return t;
    }

    /**
     * One claim's contribution. Closed: received = the final amount, rejected = what was claimed
     * beyond it. Open: received = what has come in so far, pending = the rest.
     */
    private static ClaimTotals one(boolean closed, BigDecimal claimed, BigDecimal received) {
        BigDecimal gap = claimed.subtract(received).max(BigDecimal.ZERO);
        return new ClaimTotals(1, closed ? 0 : 1, claimed, received,
            closed ? gap : BigDecimal.ZERO, closed ? BigDecimal.ZERO : gap);
    }
}
