package com.dams.cash.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.audit.service.DocumentHistoryService;
import com.dams.branch.entity.Branch;
import com.dams.branch.entity.DocType;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.DocumentNumberService;
import com.dams.cash.dto.CashDocumentPatchRequest;
import com.dams.cash.dto.CashDocumentResponse;
import com.dams.cash.dto.CreateCashDocumentRequest;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.masters.repository.BankRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cash movements — the cashier side of Stage 6. One IN-from-bank or OUT-to-bank document per
 * movement (no sub-lines), gap-free {@code C}-numbered on submit, same DRAFT → SUBMITTED
 * (→ QUERIED → SUBMITTED) cashier flow as receipts and expenses.
 *
 * A movement dated into a branch's already-closed cash day is refused ({@link CashDateLock}).
 */
@Service
public class CashDocumentService {

    private static final Logger log = LoggerFactory.getLogger(CashDocumentService.class);
    private static final String ENTITY = "CashDocument";

    private final CashDocumentRepository cashDocumentRepo;
    private final BranchRepository branchRepo;
    private final BankRepository bankRepo;
    private final AppUserRepository userRepo;
    private final DocumentNumberService documentNumberService;
    private final CashDateLock cashDateLock;
    private final CashPostingGuard guard;
    private final AuditService auditService;
    private final DocumentHistoryService documentHistoryService;

    public CashDocumentService(CashDocumentRepository cashDocumentRepo,
                               BranchRepository branchRepo,
                               BankRepository bankRepo,
                               AppUserRepository userRepo,
                               DocumentNumberService documentNumberService,
                               CashDateLock cashDateLock,
                               CashPostingGuard guard,
                               AuditService auditService,
                               DocumentHistoryService documentHistoryService) {
        this.cashDocumentRepo = cashDocumentRepo;
        this.branchRepo = branchRepo;
        this.bankRepo = bankRepo;
        this.userRepo = userRepo;
        this.documentNumberService = documentNumberService;
        this.cashDateLock = cashDateLock;
        this.guard = guard;
        this.auditService = auditService;
        this.documentHistoryService = documentHistoryService;
    }

    @Transactional(readOnly = true)
    public CashDocumentResponse get(Long id) {
        Long orgId = TenantContext.requireOrgId();
        CashDocument doc = load(orgId, id);
        guard.resolveViewBranch(orgId, doc.getBranchId()); // authorises the read for this branch
        return assemble(doc);
    }

    @Transactional(readOnly = true)
    public List<CashDocumentResponse> listForDay(Long requestedBranchId, LocalDate date) {
        Long orgId = TenantContext.requireOrgId();
        Long branchId = guard.resolveViewBranch(orgId, requestedBranchId);
        return cashDocumentRepo.findByOrgIdAndBranchIdAndTransactionDateOrderByIdAsc(orgId, branchId, date)
            .stream().map(this::assemble).toList();
    }

    @Transactional
    public CashDocumentResponse create(CreateCashDocumentRequest request) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireCashier(orgId);
        Long branchId = me.getHomeBranchId();

        cashDateLock.requireCashDateOpen(orgId, branchId, request.getTransactionDate());
        Long bankId = resolveBank(orgId, request.getBankId());

        CashDocument doc = new CashDocument();
        doc.setOrgId(orgId);
        doc.setBranchId(branchId);
        doc.setDirection(request.getDirection());
        doc.setTransactionDate(request.getTransactionDate());
        doc.setAmount(request.getAmount());
        doc.setBankId(bankId);
        doc.setTransactionRef(blankToNull(request.getTransactionRef()));
        doc.setRemark(blankToNull(request.getRemark()));
        doc.setWorkflowStatus(CashWorkflowStatus.DRAFT);
        doc.setCreatedBy(me.getId());
        doc.setLastModifiedBy(me.getId());
        doc = cashDocumentRepo.save(doc);

        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.CREATED, me.getId(),
            orderedDetail("direction", doc.getDirection().name(), "amount", doc.getAmount()));

        if (request.isSubmit()) {
            submitInternal(orgId, doc, me.getId(), false);
        }
        cashDocumentRepo.save(doc);
        log.info("Cash document {}: orgId={} docId={} branchId={} {} {} submitted={}",
            request.isSubmit() ? "created+submitted" : "created", orgId, doc.getId(), branchId,
            doc.getDirection(), doc.getAmount(), request.isSubmit());
        return assemble(doc);
    }

    @Transactional
    public CashDocumentResponse submit(Long id) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireCashier(orgId);
        CashDocument doc = load(orgId, id);
        if (doc.getWorkflowStatus() != CashWorkflowStatus.DRAFT) {
            throw DamsException.conflict("Only a draft can be submitted (document " + describe(doc)
                + " is " + doc.getWorkflowStatus() + ")");
        }
        submitInternal(orgId, doc, me.getId(), false);
        cashDocumentRepo.save(doc);
        return assemble(doc);
    }

    @Transactional
    public CashDocumentResponse resubmit(Long id) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireCashier(orgId);
        CashDocument doc = load(orgId, id);
        if (doc.getWorkflowStatus() != CashWorkflowStatus.QUERIED) {
            throw DamsException.conflict("Only a queried document can be resubmitted (document "
                + describe(doc) + " is " + doc.getWorkflowStatus() + ")");
        }
        submitInternal(orgId, doc, me.getId(), true);
        cashDocumentRepo.save(doc);
        return assemble(doc);
    }

    @Transactional
    public CashDocumentResponse patch(Long id, CashDocumentPatchRequest request) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireCashier(orgId);
        CashDocument doc = load(orgId, id);
        if (doc.getWorkflowStatus() != CashWorkflowStatus.DRAFT
            && doc.getWorkflowStatus() != CashWorkflowStatus.QUERIED) {
            throw DamsException.conflict("A cash movement can only be edited while it is a draft or"
                + " queried (document " + describe(doc) + " is " + doc.getWorkflowStatus() + ")");
        }
        if (request.getDirection() != null) {
            doc.setDirection(request.getDirection());
        }
        if (request.getTransactionDate() != null) {
            cashDateLock.requireCashDateOpen(orgId, doc.getBranchId(), request.getTransactionDate());
            doc.setTransactionDate(request.getTransactionDate());
        }
        if (request.getAmount() != null) {
            doc.setAmount(request.getAmount());
        }
        if (request.getBankId() != null) {
            doc.setBankId(resolveBank(orgId, request.getBankId()));
        }
        if (request.getTransactionRef() != null) {
            doc.setTransactionRef(blankToNull(request.getTransactionRef()));
        }
        if (request.getRemark() != null) {
            doc.setRemark(blankToNull(request.getRemark()));
        }
        doc.setLastModifiedBy(me.getId());
        cashDocumentRepo.save(doc);
        return assemble(doc);
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.requireOrgId();
        guard.requireCashier(orgId);
        CashDocument doc = load(orgId, id);
        if (doc.getWorkflowStatus() != CashWorkflowStatus.DRAFT) {
            throw DamsException.conflict("Only a draft cash movement can be deleted (document "
                + describe(doc) + " is " + doc.getWorkflowStatus() + ")");
        }
        cashDocumentRepo.delete(doc);
        log.info("Cash document deleted (draft): orgId={} docId={}", orgId, id);
    }

    // ------------------------------------------------------------------ internals

    private void submitInternal(Long orgId, CashDocument doc, Long actorId, boolean resubmit) {
        // A close may have landed while this sat in DRAFT / QUERIED — re-check the lock.
        cashDateLock.requireCashDateOpen(orgId, doc.getBranchId(), doc.getTransactionDate());
        if (doc.getDocumentNo() == null) {
            Branch branch = branchRepo.findByIdAndOrgId(doc.getBranchId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Branch", doc.getBranchId()));
            doc.setDocumentNo(documentNumberService.nextNumber(orgId, branch, DocType.C));
        }
        doc.setWorkflowStatus(CashWorkflowStatus.SUBMITTED);
        doc.setSubmittedAt(Instant.now());
        doc.setLastModifiedBy(actorId);
        auditService.recordUserEvent(ENTITY, doc.getId(), doc.getBranchId(), EventType.SUBMITTED, actorId,
            orderedDetail("documentNo", doc.getDocumentNo(), "resubmit", resubmit));
        log.info("Cash document {}: orgId={} docId={} documentNo={}",
            resubmit ? "resubmitted" : "submitted", orgId, doc.getId(), doc.getDocumentNo());
    }

    private Long resolveBank(Long orgId, Long bankId) {
        if (bankId == null) {
            return null;
        }
        return bankRepo.findByIdAndOrgId(bankId, orgId)
            .orElseThrow(() -> DamsException.notFound("Bank", bankId))
            .getId();
    }

    private CashDocument load(Long orgId, Long id) {
        return cashDocumentRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Cash document", id));
    }

    public CashDocumentResponse assemble(CashDocument doc) {
        Long orgId = doc.getOrgId();
        Branch branch = branchRepo.findByIdAndOrgId(doc.getBranchId(), orgId).orElse(null);
        String bankName = doc.getBankId() == null ? null
            : bankRepo.findByIdAndOrgId(doc.getBankId(), orgId).map(b -> b.getName()).orElse(null);
        String createdByName = userRepo.findById(doc.getCreatedBy()).map(AppUser::getName).orElse(null);

        return new CashDocumentResponse(
            doc.getId(),
            doc.getDocumentNo(),
            doc.getDirection().name(),
            doc.getTransactionDate(),
            doc.getAmount(),
            doc.getBankId(),
            bankName,
            doc.getTransactionRef(),
            doc.getRemark(),
            doc.getWorkflowStatus().name(),
            doc.getBranchId(),
            branch != null ? branch.getCode() : null,
            branch != null ? branch.getName() : null,
            doc.getCreatedBy(),
            createdByName,
            doc.getLastModifiedBy(),
            doc.getCreatedAt(),
            doc.getSubmittedAt(),
            documentHistoryService.forDocument(ENTITY, doc.getId()));
    }

    private static String describe(CashDocument doc) {
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
