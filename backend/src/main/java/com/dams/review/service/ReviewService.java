package com.dams.review.service;

import com.dams.attachment.service.AttachmentService;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.dto.CashDocumentResponse;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.cash.service.CashDocumentService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.dto.ExpenseDocumentResponse;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.expense.service.ExpenseDocumentService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.receive.dto.ReceiveDocumentResponse;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receive.service.ReceiveDocumentService;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.review.dto.FmQueue;
import com.dams.review.dto.ReviewQueueItem;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The review step: the Accountant's verify / query / reject / line-override / close-expense,
 * and the Finance Manager's approve / query / reject, plus both queues.
 *
 * Kept out of {@code ReceiveDocumentService} / {@code ExpenseDocumentService} so the maker
 * (cashier) flow stays one focused unit. This service reuses their read models
 * ({@code .get(id)}) and their derived-field recompute hooks — it never re-implements
 * pending-amount or over-limit maths.
 *
 * Guard rules live in {@link ReviewGuard}: the right role, branch in scope, and never a
 * document the caller created or last modified (maker-checker). A review transition does
 * NOT set {@code last_modified_by} — that column tracks the maker's last touch, so one
 * accountant may override a line and still verify the same document, and the FM's approval
 * is still a genuine third pair of eyes on any override.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final String RECEIVE = "ReceiveDocument";
    private static final String EXPENSE = "ExpenseDocument";
    private static final int RECENTLY_CLOSED_LIMIT = 12;

    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final ReceiveCategoryRepository receiveCategoryRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;
    private final ReceiverRepository receiverRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final BranchRepository branchRepo;
    private final BranchScope branchScope;
    private final ReviewGuard guard;
    private final AuditService auditService;
    private final AttachmentService attachmentService;
    private final ReceiveDocumentService receiveDocumentService;
    private final ExpenseDocumentService expenseDocumentService;
    private final CashDocumentRepository cashDocumentRepo;
    private final CashDocumentService cashDocumentService;

    public ReviewService(ReceiveDocumentRepository receiveDocumentRepo,
                         SettlementLineRepository settlementLineRepo,
                         ExpenseDocumentRepository expenseDocumentRepo,
                         ExpenseLineRepository expenseLineRepo,
                         JobCardRepository jobCardRepo,
                         CustomerRepository customerRepo,
                         ReceiveCategoryRepository receiveCategoryRepo,
                         ExpenseCategoryRepository expenseCategoryRepo,
                         ReceiverRepository receiverRepo,
                         ClaimCloseRepository claimCloseRepo,
                         BranchRepository branchRepo,
                         BranchScope branchScope,
                         ReviewGuard guard,
                         AuditService auditService,
                         AttachmentService attachmentService,
                         ReceiveDocumentService receiveDocumentService,
                         ExpenseDocumentService expenseDocumentService,
                         CashDocumentRepository cashDocumentRepo,
                         CashDocumentService cashDocumentService) {
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.receiveCategoryRepo = receiveCategoryRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
        this.receiverRepo = receiverRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.branchRepo = branchRepo;
        this.branchScope = branchScope;
        this.guard = guard;
        this.auditService = auditService;
        this.attachmentService = attachmentService;
        this.receiveDocumentService = receiveDocumentService;
        this.expenseDocumentService = expenseDocumentService;
        this.cashDocumentRepo = cashDocumentRepo;
        this.cashDocumentService = cashDocumentService;
    }

    // ============================================================ accountant queue

    @Transactional(readOnly = true)
    public List<ReviewQueueItem> receiptQueue() {
        Long orgId = TenantContext.requireOrgId();
        guard.requireAccountant();
        Set<Long> branchIds = branchScope.allowedBranchIds().orElseGet(Set::of);
        if (branchIds.isEmpty()) {
            return List.of();
        }
        List<ReceiveDocument> docs = receiveDocumentRepo
            .findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(orgId, WorkflowStatus.SUBMITTED, branchIds);
        return toReceiptItems(orgId, docs);
    }

    @Transactional(readOnly = true)
    public List<ReviewQueueItem> expenseQueue() {
        Long orgId = TenantContext.requireOrgId();
        guard.requireAccountant();
        Set<Long> branchIds = branchScope.allowedBranchIds().orElseGet(Set::of);
        if (branchIds.isEmpty()) {
            return List.of();
        }
        List<ExpenseDocument> docs = expenseDocumentRepo
            .findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(orgId, ExpenseWorkflowStatus.SUBMITTED, branchIds);
        return toExpenseItems(orgId, docs);
    }

    // ============================================================ finance-manager queue

    @Transactional(readOnly = true)
    public FmQueue fmReceiptQueue() {
        Long orgId = TenantContext.requireOrgId();
        guard.requireFinanceManager();

        List<ReviewQueueItem> awaiting = toReceiptItems(orgId, receiveDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, WorkflowStatus.VERIFIED));

        // Open claims: an APPROVED receipt on a claim-category job card that has not been closed.
        // One row per job card (a job card may own several documents over its life).
        List<ReceiveDocument> approved = receiveDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, WorkflowStatus.APPROVED);
        Map<Long, JobCard> jcById = jobCardsById(orgId, approved.stream().map(ReceiveDocument::getJobCardId).toList());
        Set<Long> closedJcIds = new java.util.HashSet<>(claimCloseRepo.findJobCardIdsByOrgId(orgId));
        Set<Long> claimCategoryIds = receiveCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .filter(ReceiveCategory::isClaim).map(ReceiveCategory::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> seenJobCards = new LinkedHashSet<>();
        List<ReceiveDocument> openClaimDocs = new ArrayList<>();
        for (ReceiveDocument d : approved) {
            if (!seenJobCards.add(d.getJobCardId()) || closedJcIds.contains(d.getJobCardId())) {
                continue;
            }
            JobCard jc = jcById.get(d.getJobCardId());
            if (jc != null && claimCategoryIds.contains(jc.getCategoryId())) {
                openClaimDocs.add(d);
            }
        }
        List<ReviewQueueItem> openClaims = toReceiptItems(orgId, openClaimDocs);

        List<ReviewQueueItem> recentlyClosed = recentlyClosedClaims(orgId);

        return new FmQueue(awaiting, openClaims, recentlyClosed);
    }

    @Transactional(readOnly = true)
    public FmQueue fmExpenseQueue() {
        Long orgId = TenantContext.requireOrgId();
        guard.requireFinanceManager();
        List<ReviewQueueItem> awaiting = toExpenseItems(orgId, expenseDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, ExpenseWorkflowStatus.VERIFIED));
        return new FmQueue(awaiting, List.of(), List.of());
    }

    // ============================================================ cash queue

    @Transactional(readOnly = true)
    public List<ReviewQueueItem> cashQueue() {
        Long orgId = TenantContext.requireOrgId();
        guard.requireAccountant();
        Set<Long> branchIds = branchScope.allowedBranchIds().orElseGet(Set::of);
        if (branchIds.isEmpty()) {
            return List.of();
        }
        return toCashItems(orgId, cashDocumentRepo
            .findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(orgId, CashWorkflowStatus.SUBMITTED, branchIds));
    }

    @Transactional(readOnly = true)
    public FmQueue fmCashQueue() {
        Long orgId = TenantContext.requireOrgId();
        guard.requireFinanceManager();
        List<ReviewQueueItem> awaiting = toCashItems(orgId, cashDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, CashWorkflowStatus.VERIFIED));
        return new FmQueue(awaiting, List.of(), List.of());
    }

    // ============================================================ cash transitions

    @Transactional
    public CashDocumentResponse verifyCash(Long id) {
        return transitionCash(id, guard.requireAccountant(),
            CashWorkflowStatus.SUBMITTED, CashWorkflowStatus.VERIFIED, EventType.VERIFIED, null, null);
    }

    @Transactional
    public CashDocumentResponse approveCash(Long id) {
        return transitionCash(id, guard.requireFinanceManager(),
            CashWorkflowStatus.VERIFIED, CashWorkflowStatus.APPROVED, EventType.APPROVED, null, null);
    }

    @Transactional
    public CashDocumentResponse queryCash(Long id, String note) {
        return isFinanceManager()
            ? transitionCash(id, guard.requireFinanceManager(),
                CashWorkflowStatus.VERIFIED, CashWorkflowStatus.QUERIED, EventType.QUERIED, "note", note)
            : transitionCash(id, guard.requireAccountant(),
                CashWorkflowStatus.SUBMITTED, CashWorkflowStatus.QUERIED, EventType.QUERIED, "note", note);
    }

    @Transactional
    public CashDocumentResponse rejectCash(Long id, String reason) {
        return isFinanceManager()
            ? transitionCash(id, guard.requireFinanceManager(),
                CashWorkflowStatus.VERIFIED, CashWorkflowStatus.REJECTED, EventType.REJECTED, "reason", reason)
            : transitionCash(id, guard.requireAccountant(),
                CashWorkflowStatus.SUBMITTED, CashWorkflowStatus.REJECTED, EventType.REJECTED, "reason", reason);
    }

    private CashDocumentResponse transitionCash(Long id, AppUser me, CashWorkflowStatus required,
            CashWorkflowStatus next, EventType event, String noteKey, String noteVal) {
        Long orgId = TenantContext.requireOrgId();
        CashDocument doc = cashDocumentRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Cash document", id));
        String label = doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId();
        requireState(me, doc.getBranchId(), doc.getCreatedBy(), doc.getLastModifiedBy(),
            label, doc.getWorkflowStatus() == required, doc.getWorkflowStatus());

        doc.setWorkflowStatus(next);
        cashDocumentRepo.save(doc);
        auditService.recordUserEvent("CashDocument", doc.getId(), doc.getBranchId(), event, me.getId(),
            detail("documentNo", doc.getDocumentNo(), noteKey, noteVal));
        log.info("CashDocument {}->{}: orgId={} branchId={} docId={} by={}",
            required, next, orgId, doc.getBranchId(), doc.getId(), me.getId());
        return cashDocumentService.get(id);
    }

    private List<ReviewQueueItem> toCashItems(Long orgId, List<CashDocument> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Map<Long, String> branchCodes = new HashMap<>();
        List<ReviewQueueItem> out = new ArrayList<>(docs.size());
        for (CashDocument d : docs) {
            String party = "IN".equals(d.getDirection().name()) ? "Cash IN from bank" : "Cash OUT to bank";
            out.add(new ReviewQueueItem("cash", d.getId(), d.getDocumentNo(),
                d.getBranchId(), branchCode(orgId, d.getBranchId(), branchCodes),
                party, "Cash movement", d.getAmount(), false, false, d.getSubmittedAt()));
        }
        return out;
    }

    // ============================================================ receipt transitions

    @Transactional
    public ReceiveDocumentResponse verifyReceipt(Long id) {
        return transitionReceipt(id, guard.requireAccountant(),
            WorkflowStatus.SUBMITTED, WorkflowStatus.VERIFIED, EventType.VERIFIED, null, null, false);
    }

    /** Query back to the cashier — from the Accountant (SUBMITTED) or the FM (VERIFIED). */
    @Transactional
    public ReceiveDocumentResponse queryReceipt(Long id, String note) {
        return isFinanceManager()
            ? transitionReceipt(id, guard.requireFinanceManager(),
                WorkflowStatus.VERIFIED, WorkflowStatus.QUERIED, EventType.QUERIED, "note", note, false)
            : transitionReceipt(id, guard.requireAccountant(),
                WorkflowStatus.SUBMITTED, WorkflowStatus.QUERIED, EventType.QUERIED, "note", note, false);
    }

    /** Reject (terminal) — from the Accountant (SUBMITTED) or the FM (VERIFIED). */
    @Transactional
    public ReceiveDocumentResponse rejectReceipt(Long id, String reason) {
        return isFinanceManager()
            ? transitionReceipt(id, guard.requireFinanceManager(),
                WorkflowStatus.VERIFIED, WorkflowStatus.REJECTED, EventType.REJECTED, "reason", reason, true)
            : transitionReceipt(id, guard.requireAccountant(),
                WorkflowStatus.SUBMITTED, WorkflowStatus.REJECTED, EventType.REJECTED, "reason", reason, true);
    }

    @Transactional
    public ReceiveDocumentResponse approveReceipt(Long id) {
        return transitionReceipt(id, guard.requireFinanceManager(),
            WorkflowStatus.VERIFIED, WorkflowStatus.APPROVED, EventType.APPROVED, null, null, false);
    }

    @Transactional
    public ReceiveDocumentResponse overrideReceiptLine(Long id, Integer lineNo, BigDecimal newAmount, String reason) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireAccountant();
        ReceiveDocument doc = loadReceipt(orgId, id);
        requireState(me, doc.getBranchId(), doc.getCreatedBy(), doc.getLastModifiedBy(), describe(doc),
            doc.getWorkflowStatus() == WorkflowStatus.SUBMITTED, doc.getWorkflowStatus());

        SettlementLine line = settlementLineRepo.findByOrgIdAndReceiveDocumentIdAndLineNo(orgId, id, lineNo)
            .orElseThrow(() -> DamsException.notFound("Settlement line", "lineNo", lineNo));
        BigDecimal before = line.getAmount();
        if (before.compareTo(newAmount) == 0) {
            throw DamsException.badRequest("The override amount is the same as the current amount");
        }
        line.setOverriddenBy(me.getId());
        line.setOverrideReason(reason);
        line.setOverriddenAt(Instant.now());
        if (line.getOriginalAmount() == null) {
            line.setOriginalAmount(before);   // keep the first figure the cashier entered
        }
        line.setAmount(newAmount);
        settlementLineRepo.save(line);

        auditService.recordUserEvent(RECEIVE, doc.getId(), doc.getBranchId(), EventType.OVERRIDE, me.getId(),
            detail("lineNo", lineNo, "lineId", line.getLineId(),
                "amountBefore", before, "amountAfter", newAmount, "reason", reason,
                "summary", overrideSummary(line.getLineId(), lineNo, before, newAmount, reason)));
        receiveDocumentService.refreshSettlement(id);
        log.info("SettlementLine overridden: orgId={} docId={} lineNo={} {}->{} by={}",
            orgId, doc.getId(), lineNo, before, newAmount, me.getId());
        return receiveDocumentService.get(id);
    }

    // ============================================================ expense transitions

    @Transactional
    public ExpenseDocumentResponse verifyExpense(Long id) {
        return transitionExpense(id, guard.requireAccountant(),
            ExpenseWorkflowStatus.SUBMITTED, ExpenseWorkflowStatus.VERIFIED, EventType.VERIFIED, null, null);
    }

    @Transactional
    public ExpenseDocumentResponse queryExpense(Long id, String note) {
        return isFinanceManager()
            ? transitionExpense(id, guard.requireFinanceManager(),
                ExpenseWorkflowStatus.VERIFIED, ExpenseWorkflowStatus.QUERIED, EventType.QUERIED, "note", note)
            : transitionExpense(id, guard.requireAccountant(),
                ExpenseWorkflowStatus.SUBMITTED, ExpenseWorkflowStatus.QUERIED, EventType.QUERIED, "note", note);
    }

    @Transactional
    public ExpenseDocumentResponse rejectExpense(Long id, String reason) {
        return isFinanceManager()
            ? transitionExpense(id, guard.requireFinanceManager(),
                ExpenseWorkflowStatus.VERIFIED, ExpenseWorkflowStatus.REJECTED, EventType.REJECTED, "reason", reason)
            : transitionExpense(id, guard.requireAccountant(),
                ExpenseWorkflowStatus.SUBMITTED, ExpenseWorkflowStatus.REJECTED, EventType.REJECTED, "reason", reason);
    }

    @Transactional
    public ExpenseDocumentResponse approveExpense(Long id) {
        return transitionExpense(id, guard.requireFinanceManager(),
            ExpenseWorkflowStatus.VERIFIED, ExpenseWorkflowStatus.APPROVED, EventType.APPROVED, null, null);
    }

    @Transactional
    public ExpenseDocumentResponse overrideExpenseLine(Long id, Integer lineNo, BigDecimal newAmount, String reason) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireAccountant();
        ExpenseDocument doc = loadExpense(orgId, id);
        requireState(me, doc.getBranchId(), doc.getCreatedBy(), doc.getLastModifiedBy(), describe(doc),
            doc.getWorkflowStatus() == ExpenseWorkflowStatus.SUBMITTED, doc.getWorkflowStatus());

        ExpenseLine line = expenseLineRepo.findByOrgIdAndExpenseDocumentIdAndLineNo(orgId, id, lineNo)
            .orElseThrow(() -> DamsException.notFound("Expense line", "lineNo", lineNo));
        BigDecimal before = line.getAmount();
        if (before.compareTo(newAmount) == 0) {
            throw DamsException.badRequest("The override amount is the same as the current amount");
        }
        boolean overLimitBefore = doc.isOverLimit();
        line.setOverriddenBy(me.getId());
        line.setOverrideReason(reason);
        line.setOverriddenAt(Instant.now());
        if (line.getOriginalAmount() == null) {
            line.setOriginalAmount(before);   // keep the first figure the cashier entered
        }
        line.setAmount(newAmount);
        expenseLineRepo.save(line);

        // Same recompute the cashier-side line edit runs — the flag must not go stale.
        expenseDocumentService.recomputeOverLimit(orgId, doc);
        expenseDocumentRepo.save(doc);

        auditService.recordUserEvent(EXPENSE, doc.getId(), doc.getBranchId(), EventType.OVERRIDE, me.getId(),
            detail("lineNo", lineNo, "lineId", line.getLineId(),
                "amountBefore", before, "amountAfter", newAmount, "reason", reason,
                "overLimitBefore", overLimitBefore, "overLimitAfter", doc.isOverLimit(),
                "summary", overrideSummary(line.getLineId(), lineNo, before, newAmount, reason)));
        log.info("ExpenseLine overridden: orgId={} docId={} lineNo={} {}->{} overLimit {}->{} by={}",
            orgId, doc.getId(), lineNo, before, newAmount, overLimitBefore, doc.isOverLimit(), me.getId());
        return expenseDocumentService.get(id);
    }

    /**
     * Explicit Accountant close of an expense (AGENT.md closing-rule #2). Allowed from
     * VERIFIED or APPROVED — except an over-limit expense, which needs Finance Manager
     * APPROVED first.
     */
    @Transactional
    public ExpenseDocumentResponse closeExpense(Long id) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireAccountant();
        ExpenseDocument doc = loadExpense(orgId, id);
        String label = describe(doc);
        guard.requireCanReview(me, doc.getBranchId(), doc.getCreatedBy(), doc.getLastModifiedBy(), label);

        ExpenseWorkflowStatus s = doc.getWorkflowStatus();
        if (s != ExpenseWorkflowStatus.VERIFIED && s != ExpenseWorkflowStatus.APPROVED) {
            throw DamsException.conflict("Only a verified or approved expense can be closed (document "
                + label + " is " + s + ")");
        }
        if (doc.isOverLimit() && s != ExpenseWorkflowStatus.APPROVED) {
            throw DamsException.conflict("Expense " + label + " is over its category limit — it needs "
                + "Finance Manager approval before it can be closed");
        }

        doc.setWorkflowStatus(ExpenseWorkflowStatus.CLOSED);
        expenseDocumentRepo.save(doc);

        List<Long> lineIds = expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(orgId, doc.getId())
            .stream().map(ExpenseLine::getId).toList();
        attachmentService.freezeExpenseDocument(orgId, doc.getId(), lineIds);

        auditService.recordUserEvent(EXPENSE, doc.getId(), doc.getBranchId(), EventType.CLOSED, me.getId(),
            detail("documentNo", doc.getDocumentNo(), "fromStatus", s.name()));
        log.info("ExpenseDocument closed: orgId={} branchId={} docId={} documentNo={} from={} by={}",
            orgId, doc.getBranchId(), doc.getId(), doc.getDocumentNo(), s, me.getId());
        return expenseDocumentService.get(id);
    }

    // ============================================================ transition core

    private ReceiveDocumentResponse transitionReceipt(Long id, AppUser me, WorkflowStatus required,
            WorkflowStatus next, EventType event, String noteKey, String noteVal, boolean refreshSettle) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = loadReceipt(orgId, id);
        requireState(me, doc.getBranchId(), doc.getCreatedBy(), doc.getLastModifiedBy(),
            describe(doc), doc.getWorkflowStatus() == required, doc.getWorkflowStatus());

        doc.setWorkflowStatus(next);
        receiveDocumentRepo.save(doc);
        auditService.recordUserEvent(RECEIVE, doc.getId(), doc.getBranchId(), event, me.getId(),
            detail("documentNo", doc.getDocumentNo(), noteKey, noteVal));
        if (refreshSettle) {
            receiveDocumentService.refreshSettlement(id);
        }
        log.info("ReceiveDocument {}->{}: orgId={} branchId={} docId={} by={}",
            required, next, orgId, doc.getBranchId(), doc.getId(), me.getId());
        return receiveDocumentService.get(id);
    }

    private ExpenseDocumentResponse transitionExpense(Long id, AppUser me, ExpenseWorkflowStatus required,
            ExpenseWorkflowStatus next, EventType event, String noteKey, String noteVal) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = loadExpense(orgId, id);
        requireState(me, doc.getBranchId(), doc.getCreatedBy(), doc.getLastModifiedBy(),
            describe(doc), doc.getWorkflowStatus() == required, doc.getWorkflowStatus());

        doc.setWorkflowStatus(next);
        expenseDocumentRepo.save(doc);
        auditService.recordUserEvent(EXPENSE, doc.getId(), doc.getBranchId(), event, me.getId(),
            detail("documentNo", doc.getDocumentNo(), noteKey, noteVal));
        log.info("ExpenseDocument {}->{}: orgId={} branchId={} docId={} by={}",
            required, next, orgId, doc.getBranchId(), doc.getId(), me.getId());
        return expenseDocumentService.get(id);
    }

    /** The one query/reject endpoint serves both reviewers — dispatch on the caller's role. */
    private boolean isFinanceManager() {
        return branchScope.currentRole() == Role.FINANCE_MANAGER;
    }

    /** Branch scope + maker-checker, then the workflow-state precondition. */
    private void requireState(AppUser me, Long branchId, Long createdBy, Long lastModifiedBy,
                              String label, boolean stateOk, Object actualState) {
        guard.requireCanReview(me, branchId, createdBy, lastModifiedBy, label);
        if (!stateOk) {
            throw DamsException.conflict("Document " + label + " is " + actualState
                + " — this action needs a different state");
        }
    }

    // ============================================================ internals

    private ReceiveDocument loadReceipt(Long orgId, Long id) {
        return receiveDocumentRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Receive document", id));
    }

    private ExpenseDocument loadExpense(Long orgId, Long id) {
        return expenseDocumentRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Expense document", id));
    }

    private List<ReviewQueueItem> toReceiptItems(Long orgId, List<ReceiveDocument> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, JobCard> jobCards = jobCardsById(orgId, docs.stream().map(ReceiveDocument::getJobCardId).toList());
        Map<Long, String> customerNames = customerNamesById(orgId,
            jobCards.values().stream().map(JobCard::getCustomerId).toList());
        Map<Long, String> categoryNames = receiveCategoryNames(orgId);
        Map<Long, List<SettlementLine>> linesByDoc = groupSettlementLines(orgId, docs.stream().map(ReceiveDocument::getId).toList());

        List<ReviewQueueItem> out = new ArrayList<>(docs.size());
        for (ReceiveDocument d : docs) {
            JobCard jc = jobCards.get(d.getJobCardId());
            String party = jc == null ? "—" : customerNames.getOrDefault(jc.getCustomerId(), "—");
            String category = jc == null ? "—" : categoryNames.getOrDefault(jc.getCategoryId(), "—");
            List<SettlementLine> lines = linesByDoc.getOrDefault(d.getId(), List.of());
            BigDecimal lineSum = lines.stream().map(SettlementLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = lineSum.signum() > 0 ? lineSum
                : (jc != null && jc.getInvoiceAmount() != null ? jc.getInvoiceAmount() : BigDecimal.ZERO);
            boolean hasOverride = lines.stream().anyMatch(l -> l.getOverriddenBy() != null);

            out.add(new ReviewQueueItem("receipt", d.getId(), d.getDocumentNo(),
                d.getBranchId(), branchCode(orgId, d.getBranchId(), branchCodes),
                party, category, amount, false, hasOverride, d.getSubmittedAt()));
        }
        return out;
    }

    private List<ReviewQueueItem> toExpenseItems(Long orgId, List<ExpenseDocument> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, String> receiverNames = receiverNamesById(orgId, docs.stream().map(ExpenseDocument::getReceiverId).toList());
        Map<Long, String> categoryNames = expenseCategoryNames(orgId);
        Map<Long, List<ExpenseLine>> linesByDoc = groupExpenseLines(orgId, docs.stream().map(ExpenseDocument::getId).toList());

        List<ReviewQueueItem> out = new ArrayList<>(docs.size());
        for (ExpenseDocument d : docs) {
            String party = receiverNames.getOrDefault(d.getReceiverId(), "—");
            String category = categoryNames.getOrDefault(d.getExpenseCategoryId(), "—");
            List<ExpenseLine> lines = linesByDoc.getOrDefault(d.getId(), List.of());
            BigDecimal amount = lines.stream().map(ExpenseLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean hasOverride = lines.stream().anyMatch(l -> l.getOverriddenBy() != null);

            out.add(new ReviewQueueItem("expense", d.getId(), d.getDocumentNo(),
                d.getBranchId(), branchCode(orgId, d.getBranchId(), branchCodes),
                party, category, amount, d.isOverLimit(), hasOverride, d.getSubmittedAt()));
        }
        return out;
    }

    private List<ReviewQueueItem> recentlyClosedClaims(Long orgId) {
        var closes = claimCloseRepo.findByOrgIdOrderByClosedAtDesc(orgId, Limit.of(RECENTLY_CLOSED_LIMIT));
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, JobCard> jobCards = jobCardsById(orgId, closes.stream().map(cc -> cc.getJobCardId()).toList());
        Map<Long, String> customerNames = customerNamesById(orgId,
            jobCards.values().stream().map(JobCard::getCustomerId).toList());
        Map<Long, String> categoryNames = receiveCategoryNames(orgId);
        List<ReviewQueueItem> out = new ArrayList<>(closes.size());
        for (var cc : closes) {
            JobCard jc = jobCards.get(cc.getJobCardId());
            if (jc == null) {
                continue;
            }
            String party = customerNames.getOrDefault(jc.getCustomerId(), "—");
            String category = categoryNames.getOrDefault(jc.getCategoryId(), "—");
            String code = branchCodes.getOrDefault(jc.getBranchId(), "?");
            String ref = code + "-JC-" + jc.getId();
            out.add(new ReviewQueueItem("receipt", jc.getId(), ref, jc.getBranchId(),
                code, party, category,
                cc.getFinalAmount(), false, cc.isOverridden(), cc.getClosedAt()));
        }
        return out;
    }

    // --- batched reference-data loaders (keep the queues a fixed handful of queries) ---

    private Map<Long, String> branchCodeMap(Long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (Branch b : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
            m.put(b.getId(), b.getCode());
        }
        return m;
    }

    private Map<Long, JobCard> jobCardsById(Long orgId, List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, JobCard> m = new HashMap<>();
        for (JobCard jc : jobCardRepo.findByOrgIdAndIdIn(orgId, distinct)) {
            m.put(jc.getId(), jc);
        }
        return m;
    }

    private Map<Long, String> customerNamesById(Long orgId, List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> m = new HashMap<>();
        for (Customer c : customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, distinct)) {
            m.put(c.getId(), c.getName());
        }
        return m;
    }

    private Map<Long, String> receiverNamesById(Long orgId, List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> m = new HashMap<>();
        for (Receiver r : receiverRepo.findByOrgIdAndIdIn(orgId, distinct)) {
            m.put(r.getId(), r.getName());
        }
        return m;
    }

    private Map<Long, String> receiveCategoryNames(Long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (ReceiveCategory c : receiveCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            m.put(c.getId(), c.getName());
        }
        return m;
    }

    private Map<Long, String> expenseCategoryNames(Long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (ExpenseCategory c : expenseCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            m.put(c.getId(), c.getName());
        }
        return m;
    }

    private Map<Long, List<SettlementLine>> groupSettlementLines(Long orgId, List<Long> docIds) {
        Map<Long, List<SettlementLine>> byDoc = new HashMap<>();
        for (SettlementLine l : settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(orgId, docIds)) {
            byDoc.computeIfAbsent(l.getReceiveDocumentId(), k -> new ArrayList<>()).add(l);
        }
        return byDoc;
    }

    private Map<Long, List<ExpenseLine>> groupExpenseLines(Long orgId, List<Long> docIds) {
        Map<Long, List<ExpenseLine>> byDoc = new HashMap<>();
        for (ExpenseLine l : expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(orgId, docIds)) {
            byDoc.computeIfAbsent(l.getExpenseDocumentId(), k -> new ArrayList<>()).add(l);
        }
        return byDoc;
    }

    private String branchCode(Long orgId, Long branchId, Map<Long, String> cache) {
        return cache.computeIfAbsent(branchId,
            id -> branchRepo.findByIdAndOrgId(id, orgId).map(Branch::getCode).orElse("?"));
    }

    private static String describe(ReceiveDocument doc) {
        return doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId();
    }

    private static String describe(ExpenseDocument doc) {
        return doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId();
    }

    private static String overrideSummary(String lineId, Integer lineNo, BigDecimal before, BigDecimal after, String reason) {
        String where = lineId != null ? lineId : "L" + lineNo;
        return where + ": " + money(before) + " → " + money(after) + " — " + reason;
    }

    private static String money(BigDecimal n) {
        return "₹" + n.stripTrailingZeros().toPlainString();
    }

    /** Ordered detail map that drops entries with a null key or null value (audit detail is small JSON). */
    private static Map<String, Object> detail(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i] != null && kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }
}
