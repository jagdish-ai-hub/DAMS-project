package com.dams.expense.service;

import com.dams.common.security.ActingDetails;
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
import com.dams.customer.service.PartyResolver;
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.service.JobCardService;
import com.dams.expense.dto.CreateExpenseRequest;
import com.dams.expense.dto.ExpenseDocumentResponse;
import com.dams.expense.dto.ExpenseLineInput;
import com.dams.expense.dto.ExpenseLineResponse;
import com.dams.expense.dto.ExpensePatchRequest;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.entity.PreApprovalStatus;
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
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
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
import java.util.HashMap;
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
 *    refused only once the document is CLOSED or REJECTED. Adding a line to a VERIFIED/
 *    APPROVED document reopens it to SUBMITTED — a line nobody has reviewed should never
 *    sit under an already-approved status.
 *  - "Transfer to Claim" (business status) is allowed only when the expense sits on a job
 *    card that carries a Claim Type ({@code job_card.claim_type_id}). Enforced on the
 *    create/patch path and on the dedicated endpoint.
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
    private final BankRepository bankRepo;
    private final AppUserRepository userRepo;
    private final AttachmentRepository attachmentRepo;
    private final DocumentNumberService documentNumberService;
    private final ExpensePostingGuard postingGuard;
    private final CashDateLock cashDateLock;
    private final AuditService auditService;
    private final DocumentHistoryService documentHistoryService;
    private final BranchScope branchScope;
    private final PartyResolver partyResolver;
    private final JobCardService jobCardService;

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
                                  BankRepository bankRepo,
                                  AppUserRepository userRepo,
                                  AttachmentRepository attachmentRepo,
                                  DocumentNumberService documentNumberService,
                                  ExpensePostingGuard postingGuard,
                                  CashDateLock cashDateLock,
                                  AuditService auditService,
                                  DocumentHistoryService documentHistoryService,
                                  BranchScope branchScope,
                                  PartyResolver partyResolver,
                                  JobCardService jobCardService) {
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
        this.bankRepo = bankRepo;
        this.userRepo = userRepo;
        this.attachmentRepo = attachmentRepo;
        this.documentNumberService = documentNumberService;
        this.postingGuard = postingGuard;
        this.cashDateLock = cashDateLock;
        this.auditService = auditService;
        this.documentHistoryService = documentHistoryService;
        this.branchScope = branchScope;
        this.partyResolver = partyResolver;
        this.jobCardService = jobCardService;
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
        Long postingBranchId = ActingDetails.effectiveHomeBranch(me);

        // Customer / vehicle links (rev 56) -- and, when asked, a brand-new job card for this expense.
        Linked linked = linkParty(orgId, postingBranchId, jobCard, request.getCustomerId(),
            request.getNewCustomerName(), request.getVehicleId(), request.getNewVehicleNo());
        if (jobCard == null && request.getNewJobCard() != null) {
            jobCard = createJobCardFor(orgId, request, linked);
        }
        Receiver receiver = resolveReceiver(orgId, request);
        ExpenseCategory category = requireActiveCategory(orgId, request.getExpenseCategoryId());
        ExpenseBusinessStatus status = requireActiveStatus(orgId, request.getBusinessStatusId());

        ExpenseDocument doc = new ExpenseDocument();
        doc.setOrgId(orgId);
        doc.setBranchId(ActingDetails.effectiveHomeBranch(me));   // never from the request
        doc.setJobCardId(jobCard != null ? jobCard.getId() : null);
        doc.setCustomerId(linked.customerId());
        doc.setVehicleId(linked.vehicleId());
        doc.setCustomerName(linked.customerName() != null ? linked.customerName() : blankToNull(request.getCustomerName()));
        doc.setVehicleNo(linked.vehicleNo() != null ? linked.vehicleNo() : blankToNull(request.getVehicleNo()));
        doc.setInvoiceNo(blankToNull(request.getInvoiceNo()));
        doc.setDbmId(blankToNull(request.getDbmId()));
        doc.setReceiverId(receiver.getId());
        doc.setExpenseCategoryId(category.getId());
        doc.setBusinessStatusId(status.getId());
        doc.setWorkflowStatus(ExpenseWorkflowStatus.DRAFT);
        doc.setCreatedBy(me.getId());
        doc.setLastModifiedBy(me.getId());
        doc = expenseDocumentRepo.save(doc);

        List<ExpenseLine> added = appendLines(orgId, doc, request.getLines(), me.getId(), category.getId());
        Map<String, Object> createdDetail = orderedDetail("receiverId", receiver.getId(), "jobCardId", doc.getJobCardId());
        createdDetail.put("customerId", doc.getCustomerId());
        createdDetail.put("vehicleId", doc.getVehicleId());
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.CREATED, me.getId(), createdDetail);
        for (ExpenseLine l : added) {
            auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.LINE_ADDED, me.getId(),
                orderedDetail("lineNo", l.getLineNo(), "amount", l.getAmount()));
        }

        // Before any submit: the pre-approval gate reads over_limit, so it must be current.
        recomputeOverLimit(orgId, doc);
        if (request.isSubmit()) {
            submitInternal(orgId, doc, me.getId(), false);
        }
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
        requireNotAwaitingApproval(doc);

        ExpenseLine line = appendLines(orgId, doc, List.of(input), me.getId(), doc.getExpenseCategoryId()).get(0);
        doc.setLastModifiedBy(me.getId());
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.LINE_ADDED, me.getId(),
            orderedDetail("lineNo", line.getLineNo(), "amount", line.getAmount()));

        // Same rule as the receive side: a new line on an already-reviewed document means
        // the Accountant/FM approved a smaller picture than what's now on record — reopen
        // it for re-review rather than leaving an unchecked line under an approved status.
        if (doc.getWorkflowStatus() == ExpenseWorkflowStatus.VERIFIED || doc.getWorkflowStatus() == ExpenseWorkflowStatus.APPROVED) {
            ExpenseWorkflowStatus reopenedFrom = doc.getWorkflowStatus();
            doc.setWorkflowStatus(ExpenseWorkflowStatus.SUBMITTED);
            doc.setSubmittedAt(Instant.now());
            auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.SUBMITTED, me.getId(),
                orderedDetail("documentNo", doc.getDocumentNo(), "reopenedFrom", reopenedFrom.name()));
        }

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
        requireNotAwaitingApproval(doc);
        submitInternal(orgId, doc, me.getId(), false);
        expenseDocumentRepo.save(doc);
        return assemble(doc);
    }

    /**
     * "Send for Review" (rev 53): an over-limit draft goes to the Finance Manager for
     * pre-approval instead of being submitted. The document stays DRAFT with no number and is
     * locked for the Cashier until the FM approves or queries it. Allowed from a fresh draft,
     * after an FM query, or after an approval the total has since outgrown.
     */
    @Transactional
    public ExpenseDocumentResponse requestApproval(Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        AppUser me = postingGuard.requireCanPost(orgId, jobCardOrNull(orgId, doc));

        if (doc.getWorkflowStatus() != ExpenseWorkflowStatus.DRAFT) {
            throw DamsException.conflict("Only a draft can be sent for review (document " + describe(doc)
                + " is " + doc.getWorkflowStatus() + ")");
        }
        requireNotAwaitingApproval(doc);
        List<ExpenseLine> lines = expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(orgId, doc.getId());
        if (lines.isEmpty()) {
            throw DamsException.badRequest("Add at least one expense line before sending it for review");
        }
        recomputeOverLimit(orgId, doc);
        if (!requiresFmApproval(orgId, doc)) {
            throw DamsException.badRequest("Expense " + describe(doc) + " is within every sub-category limit and"
                + " its status doesn't need Finance Manager approval — just Submit it");
        }
        BigDecimal total = sum(lines);
        if (preApprovalCovers(doc, total)) {
            throw DamsException.conflict("Expense " + describe(doc) + " is already approved for ₹"
                + doc.getPreApprovedAmount() + " — Submit it");
        }

        doc.setPreApprovalStatus(PreApprovalStatus.PENDING);
        doc.setApprovalRequestedAt(Instant.now());
        doc.setPreApprovedAmount(null);
        doc.setPreApprovedBy(null);
        doc.setPreApprovedAt(null);
        doc.setLastModifiedBy(me.getId());
        expenseDocumentRepo.save(doc);
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.APPROVAL_REQUESTED, me.getId(),
            orderedDetail("amount", total, "overLimit", doc.isOverLimit()));
        log.info("Expense sent for FM pre-approval: orgId={} docId={} amount={}", orgId, doc.getId(), total);
        return assemble(doc);
    }

    /**
     * True when an FM pre-approval covers this document as it stands: approved, and the
     * total hasn't grown past the approved amount (rev 53). Public — ReviewService uses the
     * same rule to let the Accountant close without a second FM approval.
     */
    public static boolean preApprovalCovers(ExpenseDocument doc, BigDecimal total) {
        return doc.getPreApprovalStatus() == PreApprovalStatus.APPROVED
            && doc.getPreApprovedAmount() != null
            && total.compareTo(doc.getPreApprovedAmount()) <= 0;
    }

    /**
     * rev 54 — is the expense's business status one that needs FM approval before submit
     * (flag {@code requires_fm_approval}, never the label)? Public — ReviewService's close
     * check pairs it with {@code over_limit}.
     */
    public boolean statusRequiresFmApproval(Long orgId, ExpenseDocument doc) {
        return statusRepo.findByIdAndOrgId(doc.getBusinessStatusId(), orgId)
            .map(ExpenseBusinessStatus::isRequiresFmApproval)
            .orElse(false);
    }

    /** Over any sub-category limit (rev 53), or in a status flagged for FM approval (rev 54). */
    private boolean requiresFmApproval(Long orgId, ExpenseDocument doc) {
        return doc.isOverLimit() || statusRequiresFmApproval(orgId, doc);
    }

    /** Why this expense needs FM approval — for the refusal messages. */
    private String approvalReason(ExpenseDocument doc) {
        return doc.isOverLimit() ? "is over its sub-category limit" : "is in a status that needs Finance Manager approval";
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
        requireNotAwaitingApproval(doc);

        if (request.getJobCardId() != null && !request.getJobCardId().equals(doc.getJobCardId())) {
            JobCard next = jobCardRepo.findByIdAndOrgId(request.getJobCardId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Job card", request.getJobCardId()));
            postingGuard.requireCanPost(orgId, next); // must be in the cashier's home branch
            doc.setJobCardId(next.getId());
            // The linked customer / vehicle follow the new job card (rev 56).
            if (next.getCustomerId() != null) {
                doc.setCustomerId(next.getCustomerId());
                doc.setVehicleId(next.getVehicleId());
            }
        } else if (Boolean.TRUE.equals(request.getClearJobCard())) {
            doc.setJobCardId(null);
        }
        boolean partyTouched = request.getCustomerId() != null || request.getVehicleId() != null
            || (request.getNewCustomerName() != null && !request.getNewCustomerName().isBlank())
            || (request.getNewVehicleNo() != null && !request.getNewVehicleNo().isBlank());
        if (partyTouched) {
            Linked linked = linkParty(orgId, doc.getBranchId(), jobCardOrNull(orgId, doc), request.getCustomerId(),
                request.getNewCustomerName(), request.getVehicleId(), request.getNewVehicleNo());
            doc.setCustomerId(linked.customerId());
            doc.setVehicleId(linked.vehicleId());
            if (linked.customerName() != null) {
                doc.setCustomerName(linked.customerName());
            }
            if (linked.vehicleNo() != null) {
                doc.setVehicleNo(linked.vehicleNo());
            }
        }
        if (request.getCustomerName() != null) {
            doc.setCustomerName(blankToNull(request.getCustomerName()));
        }
        if (request.getVehicleNo() != null) {
            doc.setVehicleNo(blankToNull(request.getVehicleNo()));
        }
        if (request.getInvoiceNo() != null) {
            doc.setInvoiceNo(blankToNull(request.getInvoiceNo()));
        }
        if (request.getDbmId() != null) {
            doc.setDbmId(blankToNull(request.getDbmId()));
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
        requireNotAwaitingApproval(doc);

        ExpenseLine line = expenseLineRepo.findByOrgIdAndExpenseDocumentIdAndLineNo(orgId, documentId, lineNo)
            .orElseThrow(() -> DamsException.notFound("Expense line", "lineNo", lineNo));

        // Editing a cash-mode line on an already-closed cash day would rewrite its drawer — refuse.
        ExpenseMode existingMode = expenseModeRepo.findByIdAndOrgId(line.getExpenseModeId(), orgId).orElse(null);
        cashDateLock.requireCashLineDateOpen(orgId, doc.getBranchId(), line.getTransactionDate(),
            existingMode != null && existingMode.isCash(), "expense");

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
        requireNotAwaitingApproval(doc);

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
     * Move the expense onto a claim: flips the business status to the org's
     * {@code triggers_claim} status. Any expense qualifies — no job card or claim type is
     * required (promotional-activity claims have none). Refused only while the document
     * is terminal (REJECTED / CLOSED) or awaiting FM approval.
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
        requireNotAwaitingApproval(doc);

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

    /**
     * Reviewer status change (rev 58) — an Accountant, Finance Manager or Owner moves an expense
     * to another business status from the review screen, like a receipt's job-card status.
     * Allowed while the document is SUBMITTED, VERIFIED, APPROVED or FM_QUERIED; the workflow
     * state is never changed here. A status flagged "Transfer to Claim" is audited as
     * TRANSFERRED_TO_CLAIM, anything else as STATUS_CHANGED. {@code last_modified_by} is left
     * alone on purpose: it drives maker-checker, and a reviewer's status edit must not stop
     * them (or lock out another reviewer) from verifying the same document.
     */
    @Transactional
    public ExpenseDocumentResponse changeBusinessStatus(Long documentId, Long statusId, AppUser actor) {
        Long orgId = TenantContext.requireOrgId();
        ExpenseDocument doc = load(orgId, documentId);
        if (!branchScope.canSeeBranch(doc.getBranchId())) {
            throw DamsException.forbidden("You do not have access to the branch of expense document " + documentId);
        }
        ExpenseWorkflowStatus wf = doc.getWorkflowStatus();
        if (wf != ExpenseWorkflowStatus.SUBMITTED && wf != ExpenseWorkflowStatus.VERIFIED
            && wf != ExpenseWorkflowStatus.APPROVED && wf != ExpenseWorkflowStatus.FM_QUERIED) {
            throw DamsException.conflict("The status of document " + describe(doc) + " cannot be changed while it is "
                + wf + " — only a submitted, verified, approved or Finance-queried expense can");
        }
        ExpenseBusinessStatus next = requireActiveStatus(orgId, statusId);
        if (next.getId().equals(doc.getBusinessStatusId())) {
            return assemble(doc);
        }
        ExpenseBusinessStatus before = statusRepo.findByIdAndOrgId(doc.getBusinessStatusId(), orgId).orElse(null);
        doc.setBusinessStatusId(next.getId());
        expenseDocumentRepo.save(doc);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("beforeStatusId", before != null ? before.getId() : null);
        detail.put("afterStatusId", next.getId());
        detail.put("from", before != null ? before.getName() : null);
        detail.put("to", next.getName());
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(),
            next.isTriggersClaim() ? EventType.TRANSFERRED_TO_CLAIM : EventType.STATUS_CHANGED, actor.getId(), detail);
        log.info("Expense status changed by reviewer: orgId={} docId={} {}->{} by={}",
            orgId, doc.getId(), before != null ? before.getName() : null, next.getName(), actor.getId());
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
        // Monotonic counter, never max(line_no)+1 — see ReceiveDocumentService.appendLines.
        int nextLineNo = doc.getLineNoSeq() + 1;
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
        doc.setLineNoSeq(nextLineNo - 1);
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
        // rev 53: an over-limit expense needs the FM's pre-approval before its first submit.
        // A resubmit after an Accountant query is past that gate — if its total has grown,
        // closeExpense falls back to the normal FM Approve instead.
        if (!resubmit) {
            recomputeOverLimit(orgId, doc);
            BigDecimal total = sum(lines);
            if (requiresFmApproval(orgId, doc) && !preApprovalCovers(doc, total)) {
                throw DamsException.conflict(doc.getPreApprovalStatus() == PreApprovalStatus.APPROVED
                    ? "Expense " + describe(doc) + " now totals ₹" + total + ", above the ₹" + doc.getPreApprovedAmount()
                        + " the Finance Manager approved — send it for review again before submitting"
                    : "Expense " + describe(doc) + " " + approvalReason(doc) + " — send it to the Finance Manager"
                        + " for review before submitting");
            }
        }
        // A cash-mode line added before the day-close must not slip into the locked
        // day on submit — line-add time checks are not enough. Before numbering, so a
        // refusal never consumes a document number.
        Map<Long, ExpenseMode> modes = new HashMap<>();
        for (ExpenseMode mode : expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            modes.put(mode.getId(), mode);
        }
        for (ExpenseLine l : lines) {
            ExpenseMode mode = modes.get(l.getExpenseModeId());
            if (mode == null) {
                throw DamsException.notFound("Expense mode", l.getExpenseModeId());
            }
            cashDateLock.requireCashLineDateOpen(orgId, doc.getBranchId(), l.getTransactionDate(),
                mode.isCash(), "expense");
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

    /** A draft sent for FM pre-approval is locked until the FM approves or queries it (rev 53). */
    private void requireNotAwaitingApproval(ExpenseDocument doc) {
        if (doc.getPreApprovalStatus() == PreApprovalStatus.PENDING) {
            throw DamsException.conflict("Expense " + describe(doc) + " is waiting for the Finance Manager's"
                + " approval — it can't be changed until they approve or query it");
        }
    }

    private static BigDecimal sum(List<ExpenseLine> lines) {
        return lines.stream().map(ExpenseLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    /** What an expense's customer / vehicle resolved to. Names are the display snapshot for the V30 text columns. */
    private record Linked(Long customerId, String customerName, Long vehicleId, String vehicleNo) {}

    /**
     * One rule for create and patch (rev 56): a job card that already has a customer decides the
     * customer (a conflicting pick is rejected); otherwise the picked / typed customer and vehicle
     * are resolved by {@link PartyResolver} (existing wins, typed creates, another customer's vehicle
     * number is a 409).
     */
    private Linked linkParty(Long orgId, Long branchId, JobCard jc, Long customerId, String newCustomerName,
                             Long vehicleId, String newVehicleNo) {
        if (jc != null && jc.getCustomerId() != null) {
            if (customerId != null && !customerId.equals(jc.getCustomerId())) {
                throw DamsException.badRequest("The selected customer does not match the job card's customer");
            }
            if (newCustomerName != null && !newCustomerName.isBlank()) {
                throw DamsException.badRequest("This job card already has a customer - pick it instead of typing a new one");
            }
            if (vehicleId != null && jc.getVehicleId() != null && !vehicleId.equals(jc.getVehicleId())) {
                throw DamsException.badRequest("The selected vehicle does not match the job card's vehicle");
            }
            Customer c = customerRepo.findByIdAndOrgId(jc.getCustomerId(), orgId).orElse(null);
            Vehicle v = jc.getVehicleId() == null ? null
                : vehicleRepo.findByIdAndOrgId(jc.getVehicleId(), orgId).orElse(null);
            if (v == null) {
                v = partyResolver.resolve(orgId, jc.getCustomerId(), null, null, vehicleId, newVehicleNo, branchId).vehicle();
            }
            return new Linked(jc.getCustomerId(), c != null ? c.getName() : null,
                v != null ? v.getId() : null, v != null ? v.getVehicleNo() : null);
        }
        PartyResolver.Party p = partyResolver.resolve(orgId, customerId, newCustomerName, null, vehicleId, newVehicleNo, branchId);
        return new Linked(
            p.customer() != null ? p.customer().getId() : null,
            p.customer() != null ? p.customer().getName() : null,
            p.vehicle() != null ? p.vehicle().getId() : null,
            p.vehicle() != null ? p.vehicle().getVehicleNo() : p.unlinkedVehicleNo());
    }

    /** Open the job card an expense asked for (customer optional). The job-card service enforces branch and status-role rules. */
    private JobCard createJobCardFor(Long orgId, CreateExpenseRequest request, Linked linked) {
        CreateExpenseRequest.NewJobCard spec = request.getNewJobCard();
        JobCardCreateRequest jc = new JobCardCreateRequest();
        jc.setCustomerId(linked.customerId());
        jc.setVehicleId(linked.vehicleId());
        if (linked.vehicleId() == null) {
            jc.setVehicleNo(linked.vehicleNo()); // typed-only number, kept as text until a customer exists
        }
        jc.setDbmId(request.getDbmId());
        jc.setInvoiceNo(request.getInvoiceNo());
        jc.setCategoryId(spec.getCategoryId());
        jc.setClaimTypeId(spec.getClaimTypeId());
        jc.setBusinessStatusId(spec.getBusinessStatusId());
        Long id = jobCardService.create(jc).id();
        return jobCardRepo.findByIdAndOrgId(id, orgId).orElseThrow();
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
        // The document's own link (rev 56) wins; older documents fall back to the job card's.
        Long customerLinkId = doc.getCustomerId() != null ? doc.getCustomerId() : (jc != null ? jc.getCustomerId() : null);
        Long vehicleLinkId = doc.getVehicleId() != null ? doc.getVehicleId() : (jc != null ? jc.getVehicleId() : null);
        Customer customer = customerLinkId == null ? null
            : customerRepo.findByIdAndOrgId(customerLinkId, orgId).orElse(null);
        Vehicle vehicle = vehicleLinkId == null ? null
            : vehicleRepo.findByIdAndOrgId(vehicleLinkId, orgId).orElse(null);

        // The document's own manual reference wins; fall back to the linked job card's
        // for a doc that only ever set it there (or was created before these existed).
        String customerName = doc.getCustomerName() != null ? doc.getCustomerName()
            : (customer != null ? customer.getName() : null);
        String vehicleNo = doc.getVehicleNo() != null ? doc.getVehicleNo()
            : (vehicle != null ? vehicle.getVehicleNo() : (jc != null ? jc.getVehicleNoText() : null));
        String invoiceNo = doc.getInvoiceNo() != null ? doc.getInvoiceNo()
            : (jc != null ? jc.getInvoiceNo() : null);
        String dbmId = doc.getDbmId() != null ? doc.getDbmId()
            : (jc != null ? jc.getDbmId() : null);

        String branchCode = branch != null ? branch.getCode() : "?";
        List<ExpenseLine> lines = expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(orgId, doc.getId());
        List<ExpenseLineResponse> lineDtos = toLineDtos(orgId, lines);
        BigDecimal total = lines.stream().map(ExpenseLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String createdByName = userRepo.findByIdAndOrganization_Id(doc.getCreatedBy(), orgId).map(AppUser::getName).orElse(null);

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
            jc == null ? List.of() : jobCardRepo.receiveNumbersFor(orgId, List.of(jc.getId())).stream()
                .map(row -> (String) row[1]).toList(),
            doc.getReceiverId(),
            receiver != null ? receiver.getName() : null,
            receiver != null ? receiver.getPhone() : null,
            customerLinkId,
            vehicleLinkId,
            customerName,
            vehicleNo,
            invoiceNo,
            dbmId,
            doc.getExpenseCategoryId(),
            category != null ? category.getName() : null,
            doc.getBusinessStatusId(),
            status != null ? status.getName() : null,
            status != null && status.isTriggersClaim(),
            total,
            doc.getCreatedBy(),
            createdByName,
            doc.getLastModifiedBy(),
            doc.getCreatedAt(),
            doc.getSubmittedAt(),
            doc.getPreApprovalStatus() != null ? doc.getPreApprovalStatus().name() : null,
            doc.getPreApprovedAmount(),
            doc.getPreApprovedBy() == null ? null
                : userRepo.findByIdAndOrganization_Id(doc.getPreApprovedBy(), orgId).map(AppUser::getName).orElse(null),
            doc.getPreApprovedAt(),
            doc.getApprovalRequestedAt(),
            preApprovalCovers(doc, total),
            requiresFmApproval(orgId, doc),
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
