package com.dams.dashboard.service;

import com.dams.branch.repository.BranchRepository;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.dashboard.dto.PendingWork;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.entity.PreApprovalStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.service.ExpenseDocumentService;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.review.dto.ReviewQueueItem;
import com.dams.review.service.ReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Stuck with whom" (rev 71): who has to act on every entry that is not finished yet.
 *
 * <ul>
 *   <li><b>Cashier</b> — queried and sent back; plus drafts never submitted (kept apart).</li>
 *   <li><b>Accountant</b> — submitted, FM-queried, and expenses verified / approved that only the
 *       Accountant closes.</li>
 *   <li><b>Finance Manager</b> — verified receipts and cash movements, expenses that need FM
 *       approval, claim expenses awaiting Close Claim, and pre-approval requests.</li>
 * </ul>
 * Approved receipts, closed and rejected entries are not stuck with anyone. Presentation (party,
 * category, amount) comes from {@link ReviewService}'s queue mappers so a row here always matches
 * the same row on a review screen.
 */
@Service
public class PendingWorkService {

    public static final String CASHIER = "CASHIER";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String FINANCE_MANAGER = "FINANCE_MANAGER";

    private static final List<WorkflowStatus> RECEIPT_STATES = List.of(WorkflowStatus.DRAFT,
        WorkflowStatus.QUERIED, WorkflowStatus.SUBMITTED, WorkflowStatus.FM_QUERIED, WorkflowStatus.VERIFIED);
    private static final List<ExpenseWorkflowStatus> EXPENSE_STATES = List.of(ExpenseWorkflowStatus.DRAFT,
        ExpenseWorkflowStatus.QUERIED, ExpenseWorkflowStatus.SUBMITTED, ExpenseWorkflowStatus.FM_QUERIED,
        ExpenseWorkflowStatus.VERIFIED, ExpenseWorkflowStatus.APPROVED);
    private static final List<CashWorkflowStatus> CASH_STATES = List.of(CashWorkflowStatus.DRAFT,
        CashWorkflowStatus.QUERIED, CashWorkflowStatus.SUBMITTED, CashWorkflowStatus.FM_QUERIED,
        CashWorkflowStatus.VERIFIED);

    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final BranchRepository branchRepo;
    private final ReviewService reviewService;
    private final ExpenseDocumentService expenseDocumentService;

    public PendingWorkService(ReceiveDocumentRepository receiveDocumentRepo,
                              ExpenseDocumentRepository expenseDocumentRepo,
                              CashDocumentRepository cashDocumentRepo,
                              BranchRepository branchRepo,
                              ReviewService reviewService,
                              ExpenseDocumentService expenseDocumentService) {
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.branchRepo = branchRepo;
        this.reviewService = reviewService;
        this.expenseDocumentService = expenseDocumentService;
    }

    /** Who holds an entry, why, and whether it is an unsent draft. */
    record Hold(String holder, String stage, boolean draft) {
    }

    @Transactional(readOnly = true)
    public PendingWork summary(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        if (branchId != null) {
            branchRepo.findByIdAndOrgId(branchId, orgId)
                .orElseThrow(() -> DamsException.notFound("Branch", branchId));
        }

        List<Held> held = new ArrayList<>();

        List<ReceiveDocument> receipts = receiveDocumentRepo.findForPendingWork(orgId, RECEIPT_STATES, branchId);
        Map<Long, ReviewQueueItem> receiptRows = index(reviewService.toReceiptItems(orgId, receipts));
        for (ReceiveDocument d : receipts) {
            add(held, "receipt", d.getId(), d.getWorkflowStatus().name(), receiptHold(d.getWorkflowStatus()),
                receiptRows.get(d.getId()), d.getSubmittedAt(), d.getCreatedAt());
        }

        List<ExpenseDocument> expenses = expenseDocumentRepo.findForPendingWork(orgId, EXPENSE_STATES, branchId);
        Map<Long, ReviewQueueItem> expenseRows = index(reviewService.toExpenseItems(orgId, expenses));
        Set<Long> claimStatusIds = expenseDocumentService.claimStatusIds(orgId);
        for (ExpenseDocument d : expenses) {
            ReviewQueueItem row = expenseRows.get(d.getId());
            BigDecimal total = row == null ? BigDecimal.ZERO : row.amount();
            boolean needsFm = d.isOverLimit() || expenseDocumentService.statusRequiresFmApproval(orgId, d);
            Hold h = expenseHold(d, claimStatusIds.contains(d.getBusinessStatusId()), needsFm,
                ExpenseDocumentService.preApprovalCovers(d, total));
            Instant since = d.getPreApprovalStatus() == PreApprovalStatus.PENDING && d.getApprovalRequestedAt() != null
                ? d.getApprovalRequestedAt() : d.getSubmittedAt();
            add(held, "expense", d.getId(), d.getWorkflowStatus().name(), h, row, since, d.getCreatedAt());
        }

        List<CashDocument> cash = cashDocumentRepo.findForPendingWork(orgId, CASH_STATES, branchId);
        Map<Long, ReviewQueueItem> cashRows = index(reviewService.toCashItems(orgId, cash));
        for (CashDocument d : cash) {
            add(held, "cash", d.getId(), d.getWorkflowStatus().name(), cashHold(d.getWorkflowStatus()),
                cashRows.get(d.getId()), d.getSubmittedAt(), d.getCreatedAt());
        }

        return new PendingWork(List.of(
            group(CASHIER, "Cashier", held),
            group(ACCOUNTANT, "Accountant", held),
            group(FINANCE_MANAGER, "Finance Manager", held)));
    }

    // ------------------------------------------------------------------ who holds what

    static Hold receiptHold(WorkflowStatus s) {
        return switch (s) {
            case DRAFT -> new Hold(CASHIER, "Draft — not submitted", true);
            case QUERIED -> new Hold(CASHIER, "Queried by the Accountant — needs fixing", false);
            case SUBMITTED -> new Hold(ACCOUNTANT, "Awaiting verification", false);
            case FM_QUERIED -> new Hold(ACCOUNTANT, "Queried by the Finance Manager — needs fixing", false);
            case VERIFIED -> new Hold(FINANCE_MANAGER, "Awaiting Finance Manager approval", false);
            default -> null;   // APPROVED / REJECTED: nobody is holding it
        };
    }

    static Hold cashHold(CashWorkflowStatus s) {
        return switch (s) {
            case DRAFT -> new Hold(CASHIER, "Draft — not submitted", true);
            case QUERIED -> new Hold(CASHIER, "Queried by the Accountant — needs fixing", false);
            case SUBMITTED -> new Hold(ACCOUNTANT, "Awaiting verification", false);
            case FM_QUERIED -> new Hold(ACCOUNTANT, "Queried by the Finance Manager — needs fixing", false);
            case VERIFIED -> new Hold(FINANCE_MANAGER, "Awaiting Finance Manager approval", false);
            default -> null;
        };
    }

    /**
     * @param isClaim        the expense's business status is Transfer to Claim
     * @param needsFm        over its limit, or in a status that requires FM approval
     * @param preApproved    an FM pre-approval still covers the total (no second FM step)
     */
    static Hold expenseHold(ExpenseDocument d, boolean isClaim, boolean needsFm, boolean preApproved) {
        return switch (d.getWorkflowStatus()) {
            case DRAFT -> {
                if (d.getPreApprovalStatus() == PreApprovalStatus.PENDING) {
                    yield new Hold(FINANCE_MANAGER, "Pre-approval requested", false);
                }
                if (d.getPreApprovalStatus() == PreApprovalStatus.QUERIED) {
                    yield new Hold(CASHIER, "Pre-approval queried — needs editing", false);
                }
                yield new Hold(CASHIER, "Draft — not submitted", true);
            }
            case QUERIED -> new Hold(CASHIER, "Queried by the Accountant — needs fixing", false);
            case SUBMITTED -> new Hold(ACCOUNTANT, "Awaiting verification", false);
            case FM_QUERIED -> new Hold(ACCOUNTANT, "Queried by the Finance Manager — needs fixing", false);
            case VERIFIED -> {
                if (isClaim) {
                    yield new Hold(FINANCE_MANAGER, "Awaiting Close Claim", false);
                }
                if (needsFm && !preApproved) {
                    yield new Hold(FINANCE_MANAGER, "Awaiting Finance Manager approval", false);
                }
                yield new Hold(ACCOUNTANT, "Verified — waiting for the Accountant to close", false);
            }
            case APPROVED -> isClaim
                ? new Hold(FINANCE_MANAGER, "Approved — awaiting Close Claim", false)
                : new Hold(ACCOUNTANT, "Approved — waiting for the Accountant to close", false);
            default -> null;   // CLOSED / REJECTED
        };
    }

    // ------------------------------------------------------------------ assembling

    private record Held(String holder, PendingWork.Item item) {
    }

    private static void add(List<Held> out, String type, Long id, String status, Hold hold, ReviewQueueItem row,
                            Instant submittedAt, Instant createdAt) {
        if (hold == null) {
            return;   // approved / closed / rejected — nobody is holding it
        }
        out.add(new Held(hold.holder(), new PendingWork.Item(type, id,
            row == null ? null : row.documentNo(), row == null ? null : row.branchId(),
            row == null ? "?" : row.branchCode(), row == null ? "—" : row.partyName(),
            row == null ? "—" : row.categoryName(), row == null ? BigDecimal.ZERO : row.amount(),
            status, hold.stage(), hold.draft(), submittedAt != null ? submittedAt : createdAt)));
    }

    private static Map<Long, ReviewQueueItem> index(List<ReviewQueueItem> rows) {
        return rows.stream().collect(Collectors.toMap(ReviewQueueItem::id, Function.identity(), (a, b) -> a));
    }

    private static PendingWork.Group group(String holder, String label, List<Held> all) {
        List<PendingWork.Item> items = all.stream().filter(h -> h.holder().equals(holder)).map(Held::item)
            .sorted(Comparator.comparing(PendingWork.Item::since, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        List<PendingWork.Item> waiting = items.stream().filter(i -> !i.draft()).toList();
        List<PendingWork.Item> drafts = items.stream().filter(PendingWork.Item::draft).toList();
        return new PendingWork.Group(holder, label, waiting.size(), total(waiting),
            drafts.size(), total(drafts), items);
    }

    private static BigDecimal total(List<PendingWork.Item> items) {
        return items.stream().map(PendingWork.Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
