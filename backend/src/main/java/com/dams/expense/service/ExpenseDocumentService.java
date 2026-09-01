package com.dams.expense.service;

import com.dams.attachment.entity.ParentType;
import com.dams.attachment.repository.AttachmentRepository;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.audit.service.DocumentHistoryService;
import com.dams.branch.entity.Branch;
import com.dams.branch.entity.DocType;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.DocumentNumberService;
import com.dams.cash.service.CashDateLock;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.dto.CreateExpenseRequest;
import com.dams.expense.dto.ExpenseDocumentResponse;
import com.dams.expense.dto.ExpenseLineInput;
import com.dams.expense.dto.ExpenseLineResponse;
import com.dams.expense.dto.ExpensePatchRequest;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.entity.Bank;
import com.dams.masters.entity.ExpenseBusinessStatus;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Expense documents and their lines — the cashier side of Stage 5.
 *
 * Key rules (AGENT.md / plan.md):
 *  - New Expense always creates a fresh document — there is no one-open-doc invariant
 *    (nothing aggregates toward a figure the way Pending Amount does on the receive side).
 *  - The document posts under the cashier's home branch ({@link ExpensePostingGuard}); a
 *    job-card tag, if given, must be a job card in that same branch.
 *  - The number is assigned on submit, gap-free, from the {@code E} series
 *    ({@link DocumentNumberService}); line ids ({@code {docNo}-L{n}}) are stamped then and
 *    never reused.
 *  - {@code over_limit} is recomputed after every line change: true when any line exceeds
 *    its sub-category's limit. It flags, it never blocks.
 *  - Lines stay addable until the Accountant closes the document (Stage 7) — Add Expense is
 *    refused only once the document is CLOSED or REJECTED.
 *  - "Transfer to Claim" (business status) is allowed only when the expense sits on a job
 *    card whose category is a claim category ({@code receive_category.is_claim}). Enforced
 *    on the create/patch path and on the dedicated endpoint.
 */
@Service
public class ExpenseDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseDocumentService.class);
    private static final String ENTITY = "ExpenseDocument";

    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final ReceiverRepository receiverRepo;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final BranchRepository branchRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;
    private final ExpenseSubCategoryRepository subCategoryRepo;
    private final ExpenseModeRepository expenseModeRepo;
    private final ExpenseBusinessStatusRepository statusRepo;
    private final ReceiveCategoryRepository receiveCategoryRepo;
    private final BankRepository bankRepo;
    private final AppUserRepository userRepo;
    private final AttachmentRepository attachmentRepo;
    private final DocumentNumberService documentNumberService;
    private final ExpensePostingGuard postingGuard;
    private final CashDateLock cashDateLock;
    private final AuditService auditService;
    private final DocumentHistoryService documentHistoryService;
    private final BranchScope branchScope;

    public ExpenseDocumentService(ExpenseDocumentRepository expenseDocumentRepo,
                                  ExpenseLineRepository expenseLineRepo,
                                  ReceiverRepository receiverRepo,
                                  JobCardRepository jobCardRepo,
                                  CustomerRepository customerRepo,
                                  VehicleRepository vehicleRepo,
                                  BranchRepository branchRepo,
                                  ExpenseCategoryRepository expenseCategoryRepo,
                                  ExpenseSubCategoryRepository subCategoryRepo,
                                  ExpenseModeRepository expenseModeRepo,
                                  ExpenseBusinessStatusRepository statusRepo,
                                  ReceiveCategoryRepository receiveCategoryRepo,
                                  BankRepository bankRepo,
                                  AppUserRepository userRepo,
                                  AttachmentRepository attachmentRepo,
                                  DocumentNumberService documentNumberService,
                                  ExpensePostingGuard postingGuard,
                                  CashDateLock cashDateLock,
                                  AuditService auditService,
                                  DocumentHistoryService documentHistoryService,
                                  BranchScope branchScope) {
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.receiverRepo = receiverRepo;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.branchRepo = branchRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
        this.subCategoryRepo = subCategoryRepo;
        this.expenseModeRepo = expenseModeRepo;
        this.statusRepo = statusRepo;
        this.receiveCategoryRepo = receiveCategoryRepo;
        this.bankRepo = bankRepo;
        this.userRepo = userRepo;
        this.attachmentRepo = attachmentRepo;
        this.documentNumberService = documentNumberService;
        this.postingGuard = postingGuard;
        this.cashDateLock = cashDateLock;
        this.auditService = auditService;
        this.documentHistoryService = documentHistoryService;
        this.branchScope = branchScope;
    }

    @Transactional(readOnly = true)
    public ExpenseDocumentResponse get(Long id) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, id);
        // Branch-scoped for every role (see BranchScope) — same rule as the receive side.
        if (!branchScope.canSeeBranch(doc.getBranchId())) {
            throw DamsException.forbidden("You do not have access to the branch of expense document " + id);
        }
        return assemble(doc);
    }

    /** Resolve a document's {@code lineNo} to the expense-line id — for the line attachment endpoints. */
    @Transactional(readOnly = true)
    public Long expenseLineId(Long documentId, Integer lineNo) {
        Long orgId = TenantContext.requireOrgId();
        load(orgId, documentId); // 404 / org check
        return expenseLineRepo.findByOrgIdAndExpenseDocumentIdAndLineNo(orgId, documentId, lineNo)
            .orElseThrow(() -> DamsException.notFound("Expense line", "lineNo", lineNo))
            .getId();
    }

    @Transactional
    public ExpenseDocumentResponse create(CreateExpenseRequest request) {
        Long orgId = TenantContext.requireOrgId();

        JobCard jobCard = request.getJobCardId() == null ? null
            : jobCardRepo.findByIdAndOrgId(request.getJobCardId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Job card", request.getJobCardId()));

        AppUser me = postingGuard.requireCanPost(orgId, jobCard);
        Receiver receiver = resolveReceiver(orgId, request);
        ExpenseCategory category = requireActiveCategory(orgId, request.getExpenseCategoryId());
        ExpenseBusinessStatus status = requireActiveStatus(orgId, request.getBusinessStatusId());

        if (status.isTriggersClaim()) {
            requireClaimEligible(orgId, jobCard);
        }

        ExpenseDocument doc = new ExpenseDocument();
        doc.setOrgId(orgId);
        doc.setBranchId(me.getHomeBranchId());   // never from the request
        doc.setJobCardId(jobCard != null ? jobCard.getId() : null);
        doc.setReceiverId(receiver.getId());
        doc.setExpenseCategoryId(category.getId());
        doc.setBusinessStatusId(status.getId());
        doc.setWorkflowStatus(ExpenseWorkflowStatus.DRAFT);
        doc.setCreatedBy(me.getId());
        doc.setLastModifiedBy(me.getId());
        doc = expenseDocumentRepo.save(doc);

        List<ExpenseLine> added = appendLines(orgId, doc, request.getLines(), me.getId(), category.getId());
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.CREATED, me.getId(),
            orderedDetail("receiverId", receiver.getId(), "jobCardId", doc.getJobCardId()));
        for (ExpenseLine l : added) {
            auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.LINE_ADDED, me.getId(),
                orderedDetail("lineNo", l.getLineNo(), "amount", l.getAmount()));
        }

        if (request.isSubmit()) {
            submitInternal(orgId, doc, me.getId(), false);
        }
        recomputeOverLimit(orgId, doc);
        expenseDocumentRepo.save(doc);

        log.info("Expense document created: orgId={} docId={} branchId={} jobCardId={} lines={} submitted={}",
            orgId, doc.getId(), doc.getBranchId(), doc.getJobCardId(), added.size(), request.isSubmit());
        return assemble(doc);
    }

    /** "Add Expense" — append one line. Allowed until the Accountant closes the document. */
    @Transactional
    public ExpenseDocumentResponse addLine(Long documentId, ExpenseLineInput input) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));
        requireAcceptsLines(doc);

        ExpenseLine line = appendLines(orgId, doc, List.of(input), me.getId(), doc.getExpenseCategoryId()).get(0);
        doc.setLastModifiedBy(me.getId());
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.LINE_ADDED, me.getId(),
            orderedDetail("lineNo", line.getLineNo(), "amount", line.getAmount()));
        recomputeOverLimit(orgId, doc);
        expenseDocumentRepo.save(doc);

        log.info("Expense line added: orgId={} docId={} {} amount={}",
            orgId, doc.getId(), line.getLineId() != null ? line.getLineId() : "L" + line.getLineNo(), line.getAmount());
        return assemble(doc);
    }

    @Transactional
    public ExpenseDocumentResponse submit(Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));

        if (doc.getWorkflowStatus() != ExpenseWorkflowStatus.DRAFT) {
            throw DamsException.conflict("Only a draft can be submitted (document " + describe(doc)
                + " is " + doc.getWorkflowStatus() + ")");
        }
        submitInternal(orgId, doc, me.getId(), false);
        expenseDocumentRepo.save(doc);
        return assemble(doc);
    }

    /** Fix-and-resubmit: a QUERIED document goes back to SUBMITTED after the cashier's edits. */
    @Transactional
    public ExpenseDocumentResponse resubmit(Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));

        if (doc.getWorkflowStatus() != ExpenseWorkflowStatus.QUERIED) {
            throw DamsException.conflict("Only a queried document can be resubmitted (document "
                + describe(doc) + " is " + doc.getWorkflowStatus() + ")");
        }
        submitInternal(orgId, doc, me.getId(), true);
        expenseDocumentRepo.save(doc);
        return assemble(doc);
    }

    @Transactional
    public ExpenseDocumentResponse patch(Long documentId, ExpensePatchRequest request) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));
        requireEditableHeader(doc);

        if (request.getJobCardId() != null && !request.getJobCardId().equals(doc.getJobCardId())) {
            JobCard next = jobCardRepo.findByIdAndOrgId(request.getJobCardId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Job card", request.getJobCardId()));
            postingGuard.requireCanPost(orgId, next); // must be in the cashier's home branch
            doc.setJobCardId(next.getId());
        }
        if (request.getReceiverId() != null || (request.getReceiverName() != null && !request.getReceiverName().isBlank())) {
            CreateExpenseRequest shim = new CreateExpenseRequest();
            shim.setReceiverId(request.getReceiverId());
            shim.setReceiverName(request.getReceiverName());
            shim.setReceiverPhone(request.getReceiverPhone());
            doc.setReceiverId(resolveReceiver(orgId, shim).getId());
        }
        if (request.getExpenseCategoryId() != null) {
            doc.setExpenseCategoryId(requireActiveCategory(orgId, request.getExpenseCategoryId()).getId());
        }
        if (request.getBusinessStatusId() != null) {
            ExpenseBusinessStatus next = requireActiveStatus(orgId, request.getBusinessStatusId());
            if (next.isTriggersClaim()) {
                requireClaimEligible(orgId, jobCardOrNull(orgId, doc));
            }
            doc.setBusinessStatusId(next.getId());
        }
        doc.setLastModifiedBy(me.getId());
        recomputeOverLimit(orgId, doc);
        expenseDocumentRepo.save(doc);
        log.info("Expense document patched: orgId={} docId={}", orgId, doc.getId());
        return assemble(doc);
    }

    @Transactional
    public ExpenseDocumentResponse updateLine(Long documentId, Integer lineNo, ExpenseLineInput input) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));
        requireEditableLines(doc);

        ExpenseLine line = expenseLineRepo.findByOrgIdAndExpenseDocumentIdAndLineNo(orgId, documentId, lineNo)
            .orElseThrow(() -> DamsException.notFound("Expense line", "lineNo", lineNo));
        applyLineInput(orgId, doc.getBranchId(), line, input, doc.getExpenseCategoryId());
        expenseLineRepo.save(line);
        doc.setLastModifiedBy(me.getId());
        recomputeOverLimit(orgId, doc);
        expenseDocumentRepo.save(doc);
        return assemble(doc);
    }

    @Transactional
    public ExpenseDocumentResponse deleteLine(Long documentId, Integer lineNo) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));
        requireEditableLines(doc);

        ExpenseLine line = expenseLineRepo.findByOrgIdAndExpenseDocumentIdAndLineNo(orgId, documentId, lineNo)
            .orElseThrow(() -> DamsException.notFound("Expense line", "lineNo", lineNo));
        // Removing a cash-mode line from an already-closed cash day would rewrite its drawer — refuse.
        ExpenseMode existingMode = expenseModeRepo.findByIdAndOrgId(line.getExpenseModeId(), orgId).orElse(null);
        cashDateLock.requireCashLineDateOpen(orgId, doc.getBranchId(), line.getTransactionDate(),
            existingMode != null && existingMode.isCash(), "expense");
        // line_no is not renumbered — the number (and later the line id) is never reused.
        expenseLineRepo.delete(line);
        doc.setLastModifiedBy(me.getId());
        recomputeOverLimit(orgId, doc);
        expenseDocumentRepo.save(doc);
        return assemble(doc);
    }

    /**
     * Move the expense onto a warranty / AMC / goodwill claim: flips the business status to
     * the org's {@code triggers_claim} status. Only valid when the expense sits on a job
     * card whose category is a claim category, and while the document is not already
     * terminal (REJECTED / CLOSED).
     */
    @Transactional
    public ExpenseDocumentResponse transferToClaim(Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));

        if (doc.getWorkflowStatus() == ExpenseWorkflowStatus.REJECTED
            || doc.getWorkflowStatus() == ExpenseWorkflowStatus.CLOSED) {
            throw DamsException.conflict("Document " + describe(doc) + " is " + doc.getWorkflowStatus()
                + " — it can no longer be transferred to a claim");
        }
        requireClaimEligible(orgId, jobCardOrNull(orgId, doc));

        List<ExpenseBusinessStatus> claimStatuses = statusRepo.findByOrgIdAndTriggersClaimTrue(orgId);
        if (claimStatuses.isEmpty()) {
            throw DamsException.conflict("No expense business status is marked as \"Transfer to Claim\" for this organization");
        }
        ExpenseBusinessStatus target = claimStatuses.get(0);
        Long before = doc.getBusinessStatusId();
        doc.setBusinessStatusId(target.getId());
        doc.setLastModifiedBy(me.getId());
        expenseDocumentRepo.save(doc);
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.TRANSFERRED_TO_CLAIM, me.getId(),
            orderedDetail("beforeStatusId", before, "afterStatusId", target.getId()));
        log.info("Expense document transferred to claim: orgId={} docId={} jobCardId={}",
            orgId, doc.getId(), doc.getJobCardId());
        return assemble(doc);
    }

    // ------------------------------------------------------------------ internals

    private Receiver resolveReceiver(Long orgId, CreateExpenseRequest r) {
        if (r.getReceiverId() != null) {
            return receiverRepo.findByIdAndOrgId(r.getReceiverId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Receiver", r.getReceiverId()));
        }
        if (r.getReceiverName() == null || r.getReceiverName().isBlank()) {
            throw DamsException.badRequest("Provide receiverId or receiverName for the expense");
        }
        String name = r.getReceiverName().trim();
        return receiverRepo.findFirstByOrgIdAndNameIgnoreCase(orgId, name).orElseGet(() -> {
            Receiver rec = new Receiver();
            rec.setOrgId(orgId);
            rec.setName(name);
            rec.setPhone(blankToNull(r.getReceiverPhone()));
            Receiver saved = receiverRepo.save(rec);
            log.info("Receiver created inline for expense: orgId={} receiverId={}", orgId, saved.getId());
            return saved;
        });
    }

    private List<ExpenseLine> appendLines(Long orgId, ExpenseDocument doc, List<ExpenseLineInput> inputs,
                                          Long createdByUserId, Long expenseCategoryId) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        int nextLineNo = expenseLineRepo.maxLineNo(doc.getId()) + 1;
        List<ExpenseLine> saved = new ArrayList<>();
        for (ExpenseLineInput input : inputs) {
            ExpenseLine line = new ExpenseLine();
            line.setOrgId(orgId);
            line.setExpenseDocumentId(doc.getId());
            line.setLineNo(nextLineNo);
            if (doc.getDocumentNo() != null) {
                line.setLineId(doc.getDocumentNo() + "-L" + nextLineNo);
            }
            line.setCreatedBy(createdByUserId);
            applyLineInput(orgId, doc.getBranchId(), line, input, expenseCategoryId);
            saved.add(expenseLineRepo.save(line));
            nextLineNo++;
        }
        return saved;
    }

    private void applyLineInput(Long orgId, Long branchId, ExpenseLine line, ExpenseLineInput input, Long expenseCategoryId) {
        ExpenseSubCategory sub = subCategoryRepo.findByIdAndOrgId(input.getSubCategoryId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Expense sub-category", input.getSubCategoryId()));
        if (!sub.isActive()) {
            throw DamsException.badRequest("Expense sub-category '" + sub.getName() + "' is inactive");
        }
        if (!sub.getExpenseCategoryId().equals(expenseCategoryId)) {
            throw DamsException.badRequest("Sub-category '" + sub.getName()
                + "' does not belong to this expense's category");
        }
        ExpenseMode mode = expenseModeRepo.findByIdAndOrgId(input.getExpenseModeId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Expense mode", input.getExpenseModeId()));
        if (!mode.isActive()) {
            throw DamsException.badRequest("Expense mode '" + mode.getName() + "' is inactive");
        }
        if (mode.isRequiresBank() && input.getBankId() == null) {
            throw DamsException.badRequest("Expense mode '" + mode.getName() + "' needs a bank");
        }
        if (mode.isRequiresRef() && blankToNull(input.getTransactionRef()) == null) {
            throw DamsException.badRequest("Expense mode '" + mode.getName() + "' needs a transaction reference");
        }
        // A cash-mode line dated into an already-closed cash day would silently change that
        // day's drawer position — refuse it (Stage 6).
        cashDateLock.requireCashLineDateOpen(orgId, branchId, input.getTransactionDate(), mode.isCash(), "expense");
        Long bankId = null;
        if (input.getBankId() != null) {
            bankId = bankRepo.findByIdAndOrgId(input.getBankId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Bank", input.getBankId()))
                .getId();
        }
        line.setTransactionDate(input.getTransactionDate());
        line.setSubCategoryId(sub.getId());
        line.setExpenseModeId(mode.getId());
        line.setAmount(input.getAmount());
        line.setBankId(bankId);
        line.setTransactionRef(blankToNull(input.getTransactionRef()));
        line.setRemark(blankToNull(input.getRemark()));
    }

    private void submitInternal(Long orgId, ExpenseDocument doc, Long actorId, boolean resubmit) {
        List<ExpenseLine> lines = expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(orgId, doc.getId());
        if (lines.isEmpty()) {
            throw DamsException.badRequest("Add at least one expense line before submitting");
        }
        if (doc.getDocumentNo() == null) {
            Branch branch = branchRepo.findByIdAndOrgId(doc.getBranchId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Branch", doc.getBranchId()));
            doc.setDocumentNo(documentNumberService.nextNumber(orgId, branch, DocType.E));
        }
        for (ExpenseLine l : lines) {
            if (l.getLineId() == null) {
                l.setLineId(doc.getDocumentNo() + "-L" + l.getLineNo());
            }
        }
        expenseLineRepo.saveAll(lines);

        doc.setWorkflowStatus(ExpenseWorkflowStatus.SUBMITTED);
        doc.setSubmittedAt(Instant.now());
        doc.setLastModifiedBy(actorId);
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.SUBMITTED, actorId,
            orderedDetail("documentNo", doc.getDocumentNo(), "resubmit", resubmit));
        log.info("ExpenseDocument {}: orgId={} docId={} documentNo={}",
            resubmit ? "resubmitted" : "submitted", orgId, doc.getId(), doc.getDocumentNo());
    }

    /**
     * Recompute and store {@code over_limit}: true if any line's amount exceeds its
     * sub-category limit. Public because {@link com.dams.review.service.ReviewService} runs
     * the same check after an Accountant line override — the flag must not go stale.
     * The caller saves {@code doc}.
     */
    public void recomputeOverLimit(Long orgId, ExpenseDocument doc) {
        List<ExpenseLine> lines = expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(orgId, doc.getId());
        Map<Long, BigDecimal> limits = subCategoryLimits(orgId);
        boolean over = lines.stream().anyMatch(l -> {
            BigDecimal limit = limits.get(l.getSubCategoryId());
            return limit != null && l.getAmount().compareTo(limit) > 0;
        });
        doc.setOverLimit(over);
    }

    private Map<Long, BigDecimal> subCategoryLimits(Long orgId) {
        Map<Long, BigDecimal> limits = new java.util.HashMap<>();
        for (ExpenseSubCategory s : subCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            limits.put(s.getId(), s.getLimitAmount());
        }
        return limits;
    }

    private void requireAcceptsLines(ExpenseDocument doc) {
        if (doc.getWorkflowStatus() == ExpenseWorkflowStatus.CLOSED
            || doc.getWorkflowStatus() == ExpenseWorkflowStatus.REJECTED) {
            throw DamsException.conflict("Document " + describe(doc) + " is " + doc.getWorkflowStatus()
                + " — it accepts no more expense lines");
        }
    }

    private void requireEditableLines(ExpenseDocument doc) {
        if (doc.getWorkflowStatus() != ExpenseWorkflowStatus.DRAFT
            && doc.getWorkflowStatus() != ExpenseWorkflowStatus.QUERIED) {
            throw DamsException.conflict("Expense lines can only be edited while the document is a draft"
                + " or queried (document " + describe(doc) + " is " + doc.getWorkflowStatus() + ")."
                + " Use Add Expense to add a new line to an open document.");
        }
    }

    private void requireEditableHeader(ExpenseDocument doc) {
        if (doc.getWorkflowStatus() != ExpenseWorkflowStatus.DRAFT
            && doc.getWorkflowStatus() != ExpenseWorkflowStatus.QUERIED) {
            throw DamsException.conflict("The expense header can only be edited while the document is a draft"
                + " or queried (document " + describe(doc) + " is " + doc.getWorkflowStatus() + ")");
        }
    }

    private void requireClaimEligible(Long orgId, JobCard jobCard) {
        if (jobCard == null) {
            throw DamsException.badRequest(
                "\"Transfer to Claim\" needs the expense to be tagged to a job card");
        }
        ReceiveCategory category = receiveCategoryRepo.findByIdAndOrgId(jobCard.getCategoryId(), orgId).orElse(null);
        if (category == null || !category.isClaim()) {
            throw DamsException.badRequest("Job card " + referenceOf(orgId, jobCard)
                + " is not a claim job card (its category is "
                + (category != null ? "'" + category.getName() + "'" : "unknown")
                + ") — its expenses cannot be transferred to a claim");
        }
    }

    private JobCard jobCardOrNull(Long orgId, ExpenseDocument doc) {
        return doc.getJobCardId() == null ? null
            : jobCardRepo.findByIdAndOrgId(doc.getJobCardId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Job card", doc.getJobCardId()));
    }

    private ExpenseCategory requireActiveCategory(Long orgId, Long id) {
        ExpenseCategory c = expenseCategoryRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Expense category", id));
        if (!c.isActive()) {
            throw DamsException.badRequest("Expense category '" + c.getName() + "' is inactive");
        }
        return c;
    }

    private ExpenseBusinessStatus requireActiveStatus(Long orgId, Long id) {
        ExpenseBusinessStatus s = statusRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Expense business status", id));
        if (!s.isActive()) {
            throw DamsException.badRequest("Expense business status '" + s.getName() + "' is inactive");
        }
        return s;
    }

    private ExpenseDocument load(Long orgId, Long id) {
        return expenseDocumentRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Expense document", id));
    }

    // ------------------------------------------------------------------ read model

    private ExpenseDocumentResponse assemble(ExpenseDocument doc) {
        Long orgId = doc.getOrgId();
        Branch branch = branchRepo.findByIdAndOrgId(doc.getBranchId(), orgId).orElse(null);
        Receiver receiver = receiverRepo.findByIdAndOrgId(doc.getReceiverId(), orgId).orElse(null);
        ExpenseCategory category = expenseCategoryRepo.findByIdAndOrgId(doc.getExpenseCategoryId(), orgId).orElse(null);
        ExpenseBusinessStatus status = statusRepo.findByIdAndOrgId(doc.getBusinessStatusId(), orgId).orElse(null);

        JobCard jc = doc.getJobCardId() == null ? null
            : jobCardRepo.findByIdAndOrgId(doc.getJobCardId(), orgId).orElse(null);
        Customer customer = jc == null ? null
            : customerRepo.findByIdAndOrgId(jc.getCustomerId(), orgId).orElse(null);
        Vehicle vehicle = (jc == null || jc.getVehicleId() == null) ? null
            : vehicleRepo.findByIdAndOrgId(jc.getVehicleId(), orgId).orElse(null);
        ReceiveCategory jobCategory = jc == null ? null
            : receiveCategoryRepo.findByIdAndOrgId(jc.getCategoryId(), orgId).orElse(null);
        boolean claimEligible = jobCategory != null && jobCategory.isClaim();

        String branchCode = branch != null ? branch.getCode() : "?";
        List<ExpenseLine> lines = expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(orgId, doc.getId());
        List<ExpenseLineResponse> lineDtos = toLineDtos(orgId, lines);
        BigDecimal total = lines.stream().map(ExpenseLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String createdByName = userRepo.findById(doc.getCreatedBy()).map(AppUser::getName).orElse(null);

        return new ExpenseDocumentResponse(
            doc.getId(),
            doc.getDocumentNo(),
            doc.getWorkflowStatus().name(),
            doc.isOverLimit(),
            doc.getBranchId(),
            branch != null ? branch.getCode() : null,
            branch != null ? branch.getName() : null,
            doc.getJobCardId(),
            jc != null ? JobCardResponse.reference(branchCode, jc.getId()) : null,
            doc.getReceiverId(),
            receiver != null ? receiver.getName() : null,
            receiver != null ? receiver.getPhone() : null,
            jc != null ? jc.getCustomerId() : null,
            customer != null ? customer.getName() : null,
            vehicle != null ? vehicle.getVehicleNo() : null,
            doc.getExpenseCategoryId(),
            category != null ? category.getName() : null,
            doc.getBusinessStatusId(),
            status != null ? status.getName() : null,
            status != null && status.isTriggersClaim(),
            claimEligible,
            total,
            doc.getCreatedBy(),
            createdByName,
            doc.getLastModifiedBy(),
            doc.getCreatedAt(),
            doc.getSubmittedAt(),
            lineDtos,
            documentHistoryService.forDocument(ENTITY, doc.getId()));
    }

    private List<ExpenseLineResponse> toLineDtos(Long orgId, List<ExpenseLine> lines) {
        Map<Long, ExpenseSubCategory> subs = subCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ExpenseSubCategory::getId, s -> s));
        Map<Long, String> modeNames = expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ExpenseMode::getId, ExpenseMode::getName));
        Map<Long, String> bankNames = bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(Bank::getId, Bank::getName));

        return lines.stream().map(l -> {
            ExpenseSubCategory sub = subs.get(l.getSubCategoryId());
            BigDecimal limit = sub != null ? sub.getLimitAmount() : null;
            boolean over = limit != null && l.getAmount().compareTo(limit) > 0;
            return new ExpenseLineResponse(
                l.getId(),
                l.getLineNo(),
                l.getLineId(),
                l.getTransactionDate(),
                l.getSubCategoryId(),
                sub != null ? sub.getName() : null,
                limit,
                over,
                l.getExpenseModeId(),
                modeNames.get(l.getExpenseModeId()),
                l.getAmount(),
                l.getOriginalAmount(),
                l.getBankId(),
                l.getBankId() != null ? bankNames.get(l.getBankId()) : null,
                l.getTransactionRef(),
                l.getRemark(),
                l.getOverriddenBy() != null,
                l.getOverrideReason(),
                l.getOverriddenAt(),
                (int) attachmentRepo.countByOrgIdAndParentTypeAndParentId(orgId, ParentType.EXPENSE_LINE, l.getId()),
                l.getCreatedAt());
        }).toList();
    }

    private String referenceOf(Long orgId, JobCard jc) {
        String code = branchRepo.findByIdAndOrgId(jc.getBranchId(), orgId).map(Branch::getCode).orElse("?");
        return JobCardResponse.reference(code, jc.getId());
    }

    private static String describe(ExpenseDocument doc) {
        return doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId();
    }

    private static Map<String, Object> orderedDetail(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
