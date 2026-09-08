package com.dams.estimate.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.estimate.dto.CreateEstimateRequest;
import com.dams.estimate.dto.EstimateResponse;
import com.dams.estimate.entity.Estimate;
import com.dams.estimate.entity.EstimateLine;
import com.dams.estimate.repository.EstimateLineRepository;
import com.dams.estimate.repository.EstimateRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estimates (FEAT-48): the number the customer agreed to, before the work.
 * DRAFT → APPROVED/REJECTED; re-quoting SUPERSEDES the previous live
 * estimate so the negotiation history survives. Estimates inform the bill
 * (variance shown against the final invoice) and never touch settlement math.
 * Cashiers draft, FM approves — the counter quotes, the manager stands
 * behind big numbers.
 */
@Service
public class EstimateService {

    private static final Logger log = LoggerFactory.getLogger(EstimateService.class);

    private final EstimateRepository estimateRepo;
    private final EstimateLineRepository lineRepo;
    private final JobCardRepository jobCardRepo;
    private final BranchScope branchScope;
    private final AuditService auditService;

    public EstimateService(EstimateRepository estimateRepo,
                           EstimateLineRepository lineRepo,
                           JobCardRepository jobCardRepo,
                           BranchScope branchScope,
                           AuditService auditService) {
        this.estimateRepo = estimateRepo;
        this.lineRepo = lineRepo;
        this.jobCardRepo = jobCardRepo;
        this.branchScope = branchScope;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<EstimateResponse> forJobCard(Long jobCardId) {
        Long orgId = TenantContext.requireOrgId();
        JobCard jobCard = loadCard(orgId, jobCardId);
        List<EstimateResponse> out = new ArrayList<>();
        for (Estimate e : estimateRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(orgId, jobCardId)) {
            out.add(toResponse(e, jobCard));
        }
        return out;
    }

    @Transactional
    public EstimateResponse create(CreateEstimateRequest request) {
        Long orgId = TenantContext.requireOrgId();
        JobCard jobCard = loadCard(orgId, request.getJobCardId());
        supersedeLive(orgId, jobCard.getId());

        Estimate e = new Estimate();
        e.setOrgId(orgId);
        e.setJobCardId(jobCard.getId());
        e.setCreatedBy(branchScope.currentUserId());
        e = estimateRepo.save(e);

        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 0;
        for (CreateEstimateRequest.Line l : request.getLines()) {
            if (l.getDescription() == null || l.getDescription().isBlank()) {
                throw DamsException.badRequest("Every estimate line needs a description");
            }
            if (l.getAmount() == null || l.getAmount().signum() < 0) {
                throw DamsException.badRequest("Estimate amounts cannot be negative");
            }
            EstimateLine line = new EstimateLine();
            line.setOrgId(orgId);
            line.setEstimateId(e.getId());
            line.setLineNo(++lineNo);
            line.setDescription(l.getDescription().trim());
            line.setAmount(l.getAmount());
            lineRepo.save(line);
            total = total.add(l.getAmount());
        }
        e.setTotal(total);
        e = estimateRepo.save(e);
        auditService.recordUserEvent("Estimate", e.getId(), jobCard.getBranchId(), EventType.CREATED,
            branchScope.currentUserId(), Map.of("jobCardId", jobCard.getId(), "total", total));
        log.info("Estimate created: orgId={} estimateId={} jobCardId={} total={} by={}",
            orgId, e.getId(), jobCard.getId(), total, branchScope.currentUserId());
        return toResponse(e, jobCard);
    }

    @Transactional
    public EstimateResponse decide(Long id, boolean approve, String note) {
        Long orgId = TenantContext.requireOrgId();
        Estimate e = estimateRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Estimate", id));
        if (!Estimate.DRAFT.equals(e.getStatus())) {
            throw DamsException.conflict("Estimate #" + id + " is already " + e.getStatus());
        }
        JobCard jobCard = loadCard(orgId, e.getJobCardId());
        e.setStatus(approve ? Estimate.APPROVED : Estimate.REJECTED);
        e.setApprovedBy(branchScope.currentUserId());
        e.setDecidedAt(Instant.now());
        e.setDecisionNote(note == null ? null : note.trim());
        e = estimateRepo.save(e);
        auditService.recordUserEvent("Estimate", e.getId(), jobCard.getBranchId(),
            approve ? EventType.APPROVED : EventType.REJECTED,
            branchScope.currentUserId(), Map.of("jobCardId", jobCard.getId()));
        log.info("Estimate {}: orgId={} estimateId={} by={}",
            e.getStatus(), orgId, id, branchScope.currentUserId());
        return toResponse(e, jobCard);
    }

    private void supersedeLive(Long orgId, Long jobCardId) {
        for (Estimate e : estimateRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(orgId, jobCardId)) {
            if (Estimate.DRAFT.equals(e.getStatus()) || Estimate.APPROVED.equals(e.getStatus())) {
                e.setStatus(Estimate.SUPERSEDED);
                estimateRepo.save(e);
            }
        }
    }

    private JobCard loadCard(Long orgId, Long jobCardId) {
        JobCard jobCard = jobCardRepo.findByIdAndOrgId(jobCardId, orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", jobCardId));
        if (!branchScope.canSeeBranch(jobCard.getBranchId())) {
            throw DamsException.forbidden("Job card is outside your branches");
        }
        return jobCard;
    }

    private EstimateResponse toResponse(Estimate e, JobCard jobCard) {
        List<EstimateResponse.EstimateLineResponse> lines = lineRepo
            .findByOrgIdAndEstimateIdOrderByLineNoAsc(e.getOrgId(), e.getId()).stream()
            .map(l -> new EstimateResponse.EstimateLineResponse(l.getLineNo(), l.getDescription(), l.getAmount()))
            .toList();
        BigDecimal invoice = jobCard.getInvoiceAmount();
        BigDecimal variance = invoice == null ? null : invoice.subtract(e.getTotal());
        return new EstimateResponse(
            e.getId(), e.getJobCardId(), e.getStatus(), e.getTotal(), lines,
            invoice, variance, e.getApprovedBy(), e.getDecidedAt(), e.getDecisionNote(),
            e.getCreatedBy(), e.getCreatedAt());
    }
}
