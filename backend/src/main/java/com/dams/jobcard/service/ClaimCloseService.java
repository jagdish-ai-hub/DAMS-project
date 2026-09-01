package com.dams.jobcard.service;

import com.dams.attachment.service.AttachmentService;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.jobcard.dto.CloseClaimRequest;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.common.security.BranchScope;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Finance Manager's final, immutable close of a Warranty / AMC / CG claim (AGENT.md
 * closing-rule #3). One transaction:
 *
 *   1. writes the {@link ClaimClose} row (one per job card, keyed by job_card_id);
 *   2. flips {@code settled = true} on every non-REJECTED receive document of that job card,
 *      freezing their receipts;
 *   3. records the close on the job card's audit trail.
 *
 * After this, {@code pending_amount} reports 0, {@code PATCH /job-cards} refuses category /
 * business-status changes, and {@code POST /receipts} on the job card is refused — all
 * already wired in Stage 4 against the existence of the ClaimClose row.
 *
 * Precondition: every non-REJECTED receive document on the job card must be APPROVED. You
 * finalise a claim once its money is fully through the maker-checker flow, not before.
 */
@Service
public class ClaimCloseService {

    private static final Logger log = LoggerFactory.getLogger(ClaimCloseService.class);

    private final ClaimCloseRepository claimCloseRepo;
    private final JobCardRepository jobCardRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ReceiveCategoryRepository categoryRepo;
    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final BranchScope branchScope;
    private final AttachmentService attachmentService;
    private final AuditService auditService;
    private final JobCardService jobCardService;

    public ClaimCloseService(ClaimCloseRepository claimCloseRepo,
                             JobCardRepository jobCardRepo,
                             ReceiveDocumentRepository receiveDocumentRepo,
                             SettlementLineRepository settlementLineRepo,
                             ReceiveCategoryRepository categoryRepo,
                             BranchRepository branchRepo,
                             AppUserRepository userRepo,
                             BranchScope branchScope,
                             AttachmentService attachmentService,
                             AuditService auditService,
                             JobCardService jobCardService) {
        this.claimCloseRepo = claimCloseRepo;
        this.jobCardRepo = jobCardRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.categoryRepo = categoryRepo;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.branchScope = branchScope;
        this.attachmentService = attachmentService;
        this.auditService = auditService;
        this.jobCardService = jobCardService;
    }

    @Transactional
    public JobCardResponse closeClaim(Long jobCardId, CloseClaimRequest request) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = requireFinanceManager(orgId);

        JobCard jc = jobCardRepo.findByIdAndOrgId(jobCardId, orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", jobCardId));
        String ref = reference(orgId, jc);

        ReceiveCategory category = categoryRepo.findByIdAndOrgId(jc.getCategoryId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Receive category", jc.getCategoryId()));
        if (!category.isClaim()) {
            throw DamsException.conflict("Job card " + ref + " is not a warranty / AMC / CG claim — "
                + "there is no claim to close");
        }
        if (claimCloseRepo.existsByOrgIdAndJobCardId(orgId, jobCardId)) {
            throw DamsException.conflict("Job card " + ref + "'s claim is already closed");
        }

        List<ReceiveDocument> live = receiveDocumentRepo
            .findByOrgIdAndJobCardIdOrderByCreatedAtDesc(orgId, jobCardId).stream()
            .filter(d -> d.getWorkflowStatus() != WorkflowStatus.REJECTED)
            .toList();
        if (live.isEmpty()) {
            throw DamsException.conflict("Job card " + ref + " has no receive document — "
                + "the claim receipt must be raised and approved before the claim can be closed");
        }
        for (ReceiveDocument d : live) {
            if (d.getWorkflowStatus() != WorkflowStatus.APPROVED) {
                throw DamsException.conflict("Receive document " + describe(d) + " is " + d.getWorkflowStatus()
                    + " — every receive document on this claim must be approved before the claim can be closed");
            }
        }

        BigDecimal computedTotal = settlementLineRepo.sumAmountForJobCard(orgId, jobCardId);
        BigDecimal finalAmount = request.finalAmount();
        boolean overridden = finalAmount.compareTo(computedTotal) != 0;
        String reason = request.reason() == null ? null : request.reason().trim();
        if (overridden && (reason == null || reason.isBlank())) {
            throw DamsException.badRequest("A reason is required when the final amount (" + money(finalAmount)
                + ") differs from what was received (" + money(computedTotal) + ")");
        }

        ClaimClose close = new ClaimClose();
        close.setOrgId(orgId);
        close.setJobCardId(jobCardId);
        close.setFinalAmount(finalAmount);
        close.setOverridden(overridden);
        close.setOverrideReason(overridden ? reason : null);
        close.setClosedBy(me.getId());
        claimCloseRepo.save(close);

        for (ReceiveDocument d : live) {
            if (d.isSettled()) {
                continue;
            }
            d.setSettled(true);
            receiveDocumentRepo.save(d);
            List<Long> lineIds = settlementLineRepo
                .findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(orgId, d.getId())
                .stream().map(SettlementLine::getId).toList();
            attachmentService.freezeReceiveDocument(orgId, d.getId(), lineIds);
            auditService.recordUserEvent("ReceiveDocument", d.getId(), d.getBranchId(), EventType.SETTLED, me.getId(),
                detail("via", "claimClose", "finalAmount", finalAmount));
        }

        auditService.recordUserEvent("JobCard", jobCardId, jc.getBranchId(), EventType.CLOSED, me.getId(),
            detail("finalAmount", finalAmount, "computedTotal", computedTotal,
                "overridden", overridden, "reason", overridden ? reason : null));
        log.info("Claim closed: orgId={} branchId={} jobCardId={} final={} computed={} overridden={} docsSettled={} by={}",
            orgId, jc.getBranchId(), jobCardId, finalAmount, computedTotal, overridden, live.size(), me.getId());

        return jobCardService.get(jobCardId);
    }

    private AppUser requireFinanceManager(Long orgId) {
        AppUser me = userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));
        if (me.getRole() != Role.FINANCE_MANAGER) {
            throw DamsException.forbidden("Only a Finance Manager can close a claim");
        }
        return me;
    }

    private String reference(Long orgId, JobCard jc) {
        String code = branchRepo.findByIdAndOrgId(jc.getBranchId(), orgId).map(Branch::getCode).orElse("?");
        return JobCardResponse.reference(code, jc.getId());
    }

    private static String describe(ReceiveDocument doc) {
        return doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId();
    }

    private static String money(BigDecimal n) {
        return "₹" + n.stripTrailingZeros().toPlainString();
    }

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
