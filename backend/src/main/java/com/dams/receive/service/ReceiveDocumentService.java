package com.dams.receive.service;

import com.dams.attachment.service.AttachmentService;
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
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.JobCardService;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.dto.CreateReceiptRequest;
import com.dams.receive.dto.ReceiveDocumentResponse;
import com.dams.receive.dto.SettlementLineInput;
import com.dams.receive.dto.SettlementLineResponse;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.attachment.entity.ParentType;
import com.dams.attachment.repository.AttachmentRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Receive documents and their settlement lines — the cashier side of Stage 4.
 *
 * Key rules (AGENT.md / plan.md):
 *  - "Add Payment" appends a line to the job card's one open (settled = false) document; it
 *    never creates a second — enforced here and, as a backstop, by a partial unique index.
 *  - The document always posts under the job card's branch; a cashier can only touch job
 *    cards in their own home branch ({@link ReceivePaymentGuard}).
 *  - The number is assigned on submit, gap-free ({@link DocumentNumberService}); line ids
 *    ({@code {docNo}-L{n}}) are stamped then too and never reused.
 *  - After any line change the job-card-wide Pending Amount is recomputed; at 0 (with an
 *    invoice, no closed claim) the document auto-settles and a SYSTEM audit event is written.
 *  - No new document may be opened on a job card that has a ClaimClose (409).
 */
@Service
public class ReceiveDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ReceiveDocumentService.class);
    private static final String ENTITY = "ReceiveDocument";

    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final JobCardRepository jobCardRepo;
    private final JobCardService jobCardService;
    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final BranchRepository branchRepo;
    private final ReceiveCategoryRepository categoryRepo;
    private final ReceiveBusinessStatusRepository statusRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final BankRepository bankRepo;
    private final AppUserRepository userRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final AttachmentRepository attachmentRepo;
    private final DocumentNumberService documentNumberService;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final ReceivePaymentGuard paymentGuard;
    private final CashDateLock cashDateLock;
    private final AuditService auditService;
    private final AttachmentService attachmentService;
    private final DocumentHistoryService documentHistoryService;
    private final BranchScope branchScope;

    public ReceiveDocumentService(ReceiveDocumentRepository receiveDocumentRepo,
                                  SettlementLineRepository settlementLineRepo,
                                  JobCardRepository jobCardRepo,
                                  JobCardService jobCardService,
                                  CustomerRepository customerRepo,
                                  VehicleRepository vehicleRepo,
                                  BranchRepository branchRepo,
                                  ReceiveCategoryRepository categoryRepo,
                                  ReceiveBusinessStatusRepository statusRepo,
                                  SettlementModeRepository settlementModeRepo,
                                  BankRepository bankRepo,
                                  AppUserRepository userRepo,
                                  ClaimCloseRepository claimCloseRepo,
                                  AttachmentRepository attachmentRepo,
                                  DocumentNumberService documentNumberService,
                                  PendingAmountCalculator pendingAmountCalculator,
                                  ReceivePaymentGuard paymentGuard,
                                  CashDateLock cashDateLock,
                                  AuditService auditService,
                                  AttachmentService attachmentService,
                                  DocumentHistoryService documentHistoryService,
                                  BranchScope branchScope) {
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.jobCardRepo = jobCardRepo;
        this.jobCardService = jobCardService;
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.branchRepo = branchRepo;
        this.categoryRepo = categoryRepo;
        this.statusRepo = statusRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.bankRepo = bankRepo;
        this.userRepo = userRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.attachmentRepo = attachmentRepo;
        this.documentNumberService = documentNumberService;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.paymentGuard = paymentGuard;
        this.cashDateLock = cashDateLock;
        this.auditService = auditService;
        this.attachmentService = attachmentService;
        this.documentHistoryService = documentHistoryService;
        this.branchScope = branchScope;
    }

    @Transactional(readOnly = true)
    public ReceiveDocumentResponse get(Long id) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = load(orgId, id);
        // Detail reads are branch-scoped for every role: a cashier sees only their home
        // branch (org-wide with the multi-branch toggle), an accountant their assigned
        // branches, FM / Owner everything. See BranchScope.
        if (!branchScope.canSeeBranch(doc.getBranchId())) {
            throw DamsException.forbidden("You do not have access to the branch of receive document " + id);
        }
        return assemble(doc);
    }

    /**
     * Re-run the auto-settle check after a change made outside this service — an Accountant
     * settlement-line override (Stage 7) shifts the job-card-wide Pending Amount.
     */
    @Transactional
    public void refreshSettlement(Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = load(orgId, documentId);
        JobCard jobCard = loadJobCardFor(orgId, doc);
        settleIfFullyPaid(orgId, jobCard, doc);
        receiveDocumentRepo.save(doc);
    }

    /** Resolve a document's {@code lineNo} to the settlement-line id — for the line attachment endpoints. */
    @Transactional(readOnly = true)
    public Long settlementLineId(Long documentId, Integer lineNo) {
        Long orgId = TenantContext.requireOrgId();
        load(orgId, documentId); // 404s / org check
        return settlementLineRepo.findByOrgIdAndReceiveDocumentIdAndLineNo(orgId, documentId, lineNo)
            .orElseThrow(() -> DamsException.notFound("Settlement line", "lineNo", lineNo))
            .getId();
    }

    /**
     * Create a receipt (with inline job-card create) or, if the job card already has an open
     * document, append the given lines to it — "Add Payment" via the full form.
     */
    @Transactional
    public ReceiveDocumentResponse create(CreateReceiptRequest request) {
        Long orgId = TenantContext.requireOrgId();

        JobCard jobCard = request.hasJobCardId()
            ? jobCardRepo.findByIdAndOrgId(request.getJobCardId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Job card", request.getJobCardId()))
            : createJobCardInline(orgId, request);

        AppUser me = paymentGuard.requireCanPost(orgId, jobCard);

        if (claimCloseRepo.existsByOrgIdAndJobCardId(orgId, jobCard.getId())) {
            throw DamsException.conflict("Job card " + referenceOf(orgId, jobCard)
                + " has a closed claim — no new receipt can be opened against it");
        }

        ReceiveDocument doc = receiveDocumentRepo
            .findByOrgIdAndJobCardIdAndSettledFalse(orgId, jobCard.getId())
            .orElse(null);
        boolean opened = false;
        if (doc == null) {
            doc = openDraft(orgId, jobCard, me.getId());
            opened = true;
        }

        List<SettlementLine> added = appendLines(orgId, doc, request.getLines(), me.getId());
        doc.setLastModifiedBy(me.getId());

        if (opened) {
            auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.CREATED, me.getId(),
                Map.of("jobCardId", jobCard.getId()));
        }
        for (SettlementLine l : added) {
            auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.LINE_ADDED, me.getId(),
                orderedDetail("lineNo", l.getLineNo(), "amount", l.getAmount()));
        }

        if (request.isSubmit()) {
            submitInternal(orgId, doc, me.getId(), false);
        }

        settleIfFullyPaid(orgId, jobCard, doc);
        receiveDocumentRepo.save(doc);
        log.info("Receipt {}: orgId={} docId={} jobCardId={} linesAdded={} submitted={}",
            opened ? "created" : "appended", orgId, doc.getId(), jobCard.getId(), added.size(), request.isSubmit());
        return assemble(doc);
    }

    /** "Add Payment" — one line onto the job card's open document. Never a second document. */
    @Transactional
    public ReceiveDocumentResponse addLine(Long documentId, SettlementLineInput input) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = load(orgId, documentId);
        JobCard jobCard = jobCardRepo.findByIdAndOrgId(doc.getJobCardId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", doc.getJobCardId()));
        AppUser me = paymentGuard.requireCanPost(orgId, jobCard);

        if (doc.isSettled()) {
            throw DamsException.conflict("Document " + describe(doc) + " is settled — it accepts no more payments");
        }
        if (doc.getWorkflowStatus() == WorkflowStatus.REJECTED) {
            throw DamsException.conflict("Document " + describe(doc) + " was rejected — add the payment to a new receipt");
        }

        SettlementLine line = appendLines(orgId, doc, List.of(input), me.getId()).get(0);
        doc.setLastModifiedBy(me.getId());
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.LINE_ADDED, me.getId(),
            orderedDetail("lineNo", line.getLineNo(), "amount", line.getAmount()));

        settleIfFullyPaid(orgId, jobCard, doc);
        receiveDocumentRepo.save(doc);
        log.info("Settlement line added: orgId={} docId={} {} amount={}",
            orgId, doc.getId(), line.getLineId() != null ? line.getLineId() : "L" + line.getLineNo(), line.getAmount());
        return assemble(doc);
    }

    @Transactional
    public ReceiveDocumentResponse submit(Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = load(orgId, documentId);
        JobCard jobCard = loadJobCardFor(orgId, doc);
        AppUser me = paymentGuard.requireCanPost(orgId, jobCard);

        if (doc.getWorkflowStatus() != WorkflowStatus.DRAFT) {
            throw DamsException.conflict("Only a draft can be submitted (document " + describe(doc)
                + " is " + doc.getWorkflowStatus() + ")");
        }
        submitInternal(orgId, doc, me.getId(), false);
        settleIfFullyPaid(orgId, jobCard, doc);
        receiveDocumentRepo.save(doc);
        return assemble(doc);
    }

    /** Fix-and-resubmit: a QUERIED document goes back to SUBMITTED after the cashier's edits. */
    @Transactional
    public ReceiveDocumentResponse resubmit(Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = load(orgId, documentId);
        JobCard jobCard = loadJobCardFor(orgId, doc);
        AppUser me = paymentGuard.requireCanPost(orgId, jobCard);

        if (doc.getWorkflowStatus() != WorkflowStatus.QUERIED) {
            throw DamsException.conflict("Only a queried document can be resubmitted (document "
                + describe(doc) + " is " + doc.getWorkflowStatus() + ")");
        }
        submitInternal(orgId, doc, me.getId(), true);
        settleIfFullyPaid(orgId, jobCard, doc);
        receiveDocumentRepo.save(doc);
        return assemble(doc);
    }

    @Transactional
    public ReceiveDocumentResponse updateLine(Long documentId, Integer lineNo, SettlementLineInput input) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = load(orgId, documentId);
        JobCard jobCard = loadJobCardFor(orgId, doc);
        AppUser me = paymentGuard.requireCanPost(orgId, jobCard);
        requireEditableLines(doc);

        SettlementLine line = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdAndLineNo(orgId, documentId, lineNo)
            .orElseThrow(() -> DamsException.notFound("Settlement line", "lineNo", lineNo));

        applyLineInput(orgId, doc.getBranchId(), line, input);
        settlementLineRepo.save(line);
        doc.setLastModifiedBy(me.getId());
        receiveDocumentRepo.save(doc);
        settleIfFullyPaid(orgId, jobCard, doc);
        return assemble(doc);
    }

    @Transactional
    public ReceiveDocumentResponse deleteLine(Long documentId, Integer lineNo) {
        Long orgId = TenantContext.requireOrgId();
        ReceiveDocument doc = load(orgId, documentId);
        JobCard jobCard = loadJobCardFor(orgId, doc);
        AppUser me = paymentGuard.requireCanPost(orgId, jobCard);
        requireEditableLines(doc);

        SettlementLine line = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdAndLineNo(orgId, documentId, lineNo)
            .orElseThrow(() -> DamsException.notFound("Settlement line", "lineNo", lineNo));
        // Removing a cash-mode line from an already-closed cash day would rewrite its drawer — refuse.
        SettlementMode existingMode = settlementModeRepo.findByIdAndOrgId(line.getSettlementModeId(), orgId).orElse(null);
        cashDateLock.requireCashLineDateOpen(orgId, doc.getBranchId(), line.getTransactionDate(),
            existingMode != null && existingMode.isCash(), "settlement");
        // line_no is not renumbered — the number (and later the line id) is never reused.
        settlementLineRepo.delete(line);
        doc.setLastModifiedBy(me.getId());
        receiveDocumentRepo.save(doc);
        return assemble(doc);
    }

    // ------------------------------------------------------------------ internals

    private JobCard createJobCardInline(Long orgId, CreateReceiptRequest r) {
        if (r.getCategoryId() == null || r.getBusinessStatusId() == null) {
            throw DamsException.badRequest(
                "categoryId and businessStatusId are required when creating a job card inline");
        }
        JobCardCreateRequest jc = new JobCardCreateRequest();
        jc.setCustomerId(r.getCustomerId());
        jc.setCustomerName(r.getCustomerName());
        jc.setCustomerPhone(r.getCustomerPhone());
        jc.setVehicleId(r.getVehicleId());
        jc.setVehicleNo(r.getVehicleNo());
        jc.setDbmId(r.getDbmId());
        jc.setInvoiceNo(r.getInvoiceNo());
        jc.setInvoiceAmount(r.getInvoiceAmount());
        jc.setB2b(r.getB2b());
        jc.setGstNo(r.getGstNo());
        jc.setCategoryId(r.getCategoryId());
        jc.setBusinessStatusId(r.getBusinessStatusId());
        // JobCardService forces a cashier's branch to their home branch and validates B2B/GST.
        JobCardResponse created = jobCardService.create(jc);
        return jobCardRepo.findByIdAndOrgId(created.id(), orgId).orElseThrow();
    }

    private ReceiveDocument openDraft(Long orgId, JobCard jobCard, Long userId) {
        ReceiveDocument doc = new ReceiveDocument();
        doc.setOrgId(orgId);
        doc.setBranchId(jobCard.getBranchId());   // never from the request
        doc.setJobCardId(jobCard.getId());
        doc.setWorkflowStatus(WorkflowStatus.DRAFT);
        doc.setCreatedBy(userId);
        doc.setLastModifiedBy(userId);
        return receiveDocumentRepo.save(doc);
    }

    private List<SettlementLine> appendLines(Long orgId, ReceiveDocument doc,
                                             List<SettlementLineInput> inputs, Long createdByUserId) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        int nextLineNo = settlementLineRepo.maxLineNo(doc.getId()) + 1;
        List<SettlementLine> saved = new java.util.ArrayList<>();
        for (SettlementLineInput input : inputs) {
            SettlementLine line = new SettlementLine();
            line.setOrgId(orgId);
            line.setReceiveDocumentId(doc.getId());
            line.setLineNo(nextLineNo);
            // If the document already has a number (Add Payment onto a submitted doc), stamp
            // the line id now; otherwise it is stamped at submit.
            if (doc.getDocumentNo() != null) {
                line.setLineId(doc.getDocumentNo() + "-L" + nextLineNo);
            }
            line.setCreatedBy(createdByUserId);
            applyLineInput(orgId, doc.getBranchId(), line, input);
            saved.add(settlementLineRepo.save(line));
            nextLineNo++;
        }
        return saved;
    }

    private void applyLineInput(Long orgId, Long branchId, SettlementLine line, SettlementLineInput input) {
        SettlementMode mode = settlementModeRepo.findByIdAndOrgId(input.getSettlementModeId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Settlement mode", input.getSettlementModeId()));
        if (!mode.isActive()) {
            throw DamsException.badRequest("Settlement mode '" + mode.getName() + "' is inactive");
        }
        if (mode.isRequiresBank() && input.getBankId() == null) {
            throw DamsException.badRequest("Settlement mode '" + mode.getName() + "' needs a bank");
        }
        // A cash-mode line dated into an already-closed cash day would silently change that
        // day's drawer position — refuse it (Stage 6).
        cashDateLock.requireCashLineDateOpen(orgId, branchId, input.getTransactionDate(), mode.isCash(), "settlement");
        Long bankId = null;
        if (input.getBankId() != null) {
            bankId = bankRepo.findByIdAndOrgId(input.getBankId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Bank", input.getBankId()))
                .getId();
        }
        line.setTransactionDate(input.getTransactionDate());
        line.setSettlementModeId(mode.getId());
        line.setAmount(input.getAmount());
        line.setBankId(bankId);
        line.setTransactionRef(blankToNull(input.getTransactionRef()));
        line.setRemark(blankToNull(input.getRemark()));
    }

    private void submitInternal(Long orgId, ReceiveDocument doc, Long actorId, boolean resubmit) {
        List<SettlementLine> lines = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(orgId, doc.getId());
        if (lines.isEmpty()) {
            throw DamsException.badRequest("Add at least one settlement line before submitting");
        }
        if (doc.getDocumentNo() == null) {
            Branch branch = branchRepo.findByIdAndOrgId(doc.getBranchId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Branch", doc.getBranchId()));
            doc.setDocumentNo(documentNumberService.nextNumber(orgId, branch, DocType.R));
        }
        for (SettlementLine l : lines) {
            if (l.getLineId() == null) {
                l.setLineId(doc.getDocumentNo() + "-L" + l.getLineNo());
            }
        }
        settlementLineRepo.saveAll(lines);

        doc.setWorkflowStatus(WorkflowStatus.SUBMITTED);
        doc.setSubmittedAt(Instant.now());
        doc.setLastModifiedBy(actorId);
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.SUBMITTED, actorId,
            orderedDetail("documentNo", doc.getDocumentNo(), "resubmit", resubmit));
        log.info("ReceiveDocument {}: orgId={} docId={} documentNo={}",
            resubmit ? "resubmitted" : "submitted", orgId, doc.getId(), doc.getDocumentNo());
    }

    /**
     * Recompute the job-card-wide Pending Amount; if it is 0 (invoice present, no closed
     * claim) flip the open document to settled and record a SYSTEM event. The
     * claim-close-forces-settled path is added in Stage 8.
     */
    private void settleIfFullyPaid(Long orgId, JobCard jobCard, ReceiveDocument doc) {
        if (doc.isSettled()
            || doc.getWorkflowStatus() == WorkflowStatus.DRAFT
            || doc.getWorkflowStatus() == WorkflowStatus.REJECTED) {
            return;
        }
        if (jobCard.getInvoiceAmount() == null || pendingAmountCalculator.hasClaimClose(orgId, jobCard.getId())) {
            return;
        }
        if (pendingAmountCalculator.forJobCard(jobCard).signum() != 0) {
            return;
        }
        doc.setSettled(true);
        auditService.recordSystemEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.SETTLED,
            orderedDetail("pendingAmount", BigDecimal.ZERO, "invoiceAmount", jobCard.getInvoiceAmount()));

        List<Long> lineIds = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(orgId, doc.getId())
            .stream().map(SettlementLine::getId).toList();
        attachmentService.freezeReceiveDocument(orgId, doc.getId(), lineIds);

        log.info("ReceiveDocument auto-settled at pending 0: orgId={} docId={} documentNo={}",
            orgId, doc.getId(), doc.getDocumentNo());
    }

    private void requireEditableLines(ReceiveDocument doc) {
        if (doc.getWorkflowStatus() != WorkflowStatus.DRAFT && doc.getWorkflowStatus() != WorkflowStatus.QUERIED) {
            throw DamsException.conflict("Settlement lines can only be edited while the document is a draft"
                + " or queried (document " + describe(doc) + " is " + doc.getWorkflowStatus() + ")."
                + " Use Add Payment to add a new line to an open document.");
        }
    }

    private ReceiveDocument load(Long orgId, Long id) {
        return receiveDocumentRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Receive document", id));
    }

    private JobCard loadJobCardFor(Long orgId, ReceiveDocument doc) {
        return jobCardRepo.findByIdAndOrgId(doc.getJobCardId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", doc.getJobCardId()));
    }

    // ------------------------------------------------------------------ read model

    private ReceiveDocumentResponse assemble(ReceiveDocument doc) {
        Long orgId = doc.getOrgId();
        JobCard jc = jobCardRepo.findByIdAndOrgId(doc.getJobCardId(), orgId).orElseThrow();
        Branch branch = branchRepo.findByIdAndOrgId(doc.getBranchId(), orgId).orElse(null);
        Customer customer = customerRepo.findByIdAndOrgId(jc.getCustomerId(), orgId).orElse(null);
        Vehicle vehicle = jc.getVehicleId() == null ? null
            : vehicleRepo.findByIdAndOrgId(jc.getVehicleId(), orgId).orElse(null);
        ReceiveCategory category = categoryRepo.findByIdAndOrgId(jc.getCategoryId(), orgId).orElse(null);
        ReceiveBusinessStatus status = statusRepo.findByIdAndOrgId(jc.getBusinessStatusId(), orgId).orElse(null);
        ClaimClose claimClose = claimCloseRepo.findByOrgIdAndJobCardId(orgId, jc.getId()).orElse(null);

        List<SettlementLine> lines = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(orgId, doc.getId());
        List<SettlementLineResponse> lineDtos = toLineDtos(orgId, lines);

        BigDecimal totalReceived = lines.stream()
            .map(SettlementLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = pendingAmountCalculator.forJobCard(jc);
        boolean claimClosed = claimClose != null;

        String createdByName = userRepo.findById(doc.getCreatedBy()).map(AppUser::getName).orElse(null);
        String branchCode = branch != null ? branch.getCode() : "?";

        return new ReceiveDocumentResponse(
            doc.getId(),
            doc.getDocumentNo(),
            doc.getWorkflowStatus().name(),
            doc.isSettled(),
            jc.getId(),
            JobCardResponse.reference(branchCode, jc.getId()),
            doc.getBranchId(),
            branch != null ? branch.getCode() : null,
            branch != null ? branch.getName() : null,
            jc.getCustomerId(),
            customer != null ? customer.getName() : null,
            customer != null ? customer.getPhone() : null,
            vehicle != null ? vehicle.getVehicleNo() : null,
            jc.getDbmId(),
            jc.getInvoiceNo(),
            jc.getInvoiceAmount(),
            jc.isB2b(),
            jc.getGstNo(),
            jc.getCategoryId(),
            category != null ? category.getName() : null,
            category != null && category.isClaim(),
            jc.getBusinessStatusId(),
            status != null ? status.getName() : null,
            pending,
            claimClosed,
            claimClose != null ? claimClose.getFinalAmount() : null,
            claimClose != null && claimClose.isOverridden(),
            claimClose != null ? claimClose.getOverrideReason() : null,
            totalReceived,
            paymentGuard.canRecordPayment(orgId, jc, pending, claimClosed) && !doc.isSettled(),
            doc.getCreatedBy(),
            createdByName,
            doc.getLastModifiedBy(),
            doc.getCreatedAt(),
            doc.getSubmittedAt(),
            lineDtos,
            documentHistoryService.forDocument(ENTITY, doc.getId()));
    }

    private List<SettlementLineResponse> toLineDtos(Long orgId, List<SettlementLine> lines) {
        Map<Long, String> modeNames = settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(SettlementMode::getId, SettlementMode::getName));
        Map<Long, String> bankNames = bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(b -> b.getId(), b -> b.getName()));

        return lines.stream().map(l -> new SettlementLineResponse(
            l.getId(),
            l.getLineNo(),
            l.getLineId(),
            l.getTransactionDate(),
            l.getSettlementModeId(),
            modeNames.get(l.getSettlementModeId()),
            l.getAmount(),
            l.getOriginalAmount(),
            l.getBankId(),
            l.getBankId() != null ? bankNames.get(l.getBankId()) : null,
            l.getTransactionRef(),
            l.getRemark(),
            l.getOverriddenBy() != null,
            l.getOverrideReason(),
            l.getOverriddenAt(),
            (int) attachmentRepo.countByOrgIdAndParentTypeAndParentId(orgId, ParentType.SETTLEMENT_LINE, l.getId()),
            l.getCreatedAt()
        )).toList();
    }

    private String referenceOf(Long orgId, JobCard jc) {
        String code = branchRepo.findByIdAndOrgId(jc.getBranchId(), orgId).map(Branch::getCode).orElse("?");
        return JobCardResponse.reference(code, jc.getId());
    }

    private static String describe(ReceiveDocument doc) {
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
