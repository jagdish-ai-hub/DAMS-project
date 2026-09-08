package com.dams.jobcard.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.jobcard.dto.ClaimActionResponse;
import com.dams.jobcard.dto.CreateClaimActionRequest;
import com.dams.jobcard.entity.ClaimAction;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimActionRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Claim next-action tracker (FEAT-38). The FM queue shows old claims; this
 * records the next step with an owner and a date. Only FM and Owner write —
 * the chase belongs to the claim owner, not the counter. Overdue is derived
 * at read time, like follow-ups.
 */
@Service
public class ClaimActionService {

    private static final Logger log = LoggerFactory.getLogger(ClaimActionService.class);

    private final ClaimActionRepository actionRepo;
    private final JobCardRepository jobCardRepo;
    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final BranchScope branchScope;
    private final AuditService auditService;

    public ClaimActionService(ClaimActionRepository actionRepo,
                              JobCardRepository jobCardRepo,
                              BranchRepository branchRepo,
                              AppUserRepository userRepo,
                              BranchScope branchScope,
                              AuditService auditService) {
        this.actionRepo = actionRepo;
        this.jobCardRepo = jobCardRepo;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.branchScope = branchScope;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ClaimActionResponse> openActions() {
        Long orgId = TenantContext.requireOrgId();
        LocalDate today = OrgTime.today();
        List<ClaimActionResponse> out = new ArrayList<>();
        for (ClaimAction a : actionRepo.findByOrgIdAndDoneAtIsNullOrderByDueDateAsc(orgId)) {
            JobCard jobCard = jobCardRepo.findByIdAndOrgId(a.getJobCardId(), orgId).orElse(null);
            if (jobCard == null || !branchScope.canSeeBranch(jobCard.getBranchId())) {
                continue;
            }
            out.add(toResponse(orgId, a, jobCard, a.getDueDate().isBefore(today)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ClaimActionResponse> forJobCard(Long jobCardId) {
        Long orgId = TenantContext.requireOrgId();
        JobCard jobCard = jobCardRepo.findByIdAndOrgId(jobCardId, orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", jobCardId));
        LocalDate today = OrgTime.today();
        List<ClaimActionResponse> out = new ArrayList<>();
        for (ClaimAction a : actionRepo.findByOrgIdAndJobCardIdOrderByDueDateAsc(orgId, jobCardId)) {
            out.add(toResponse(orgId, a, jobCard, a.isOpen() && a.getDueDate().isBefore(today)));
        }
        return out;
    }

    @Transactional
    public ClaimActionResponse create(CreateClaimActionRequest request) {
        Long orgId = TenantContext.requireOrgId();
        JobCard jobCard = jobCardRepo.findByIdAndOrgId(request.getJobCardId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", request.getJobCardId()));
        if (request.getOwnerUserId() != null) {
            var owner = userRepo.findByIdAndOrganization_Id(request.getOwnerUserId(), orgId)
                .orElseThrow(() -> DamsException.notFound("User", request.getOwnerUserId()));
            if (owner.getRole() != Role.FINANCE_MANAGER && owner.getRole() != Role.OWNER) {
                throw DamsException.badRequest("Claim actions can only be owned by the Finance Manager or Owner");
            }
        }
        ClaimAction a = new ClaimAction();
        a.setOrgId(orgId);
        a.setJobCardId(request.getJobCardId());
        a.setAction(request.getAction().trim());
        a.setOwnerUserId(request.getOwnerUserId());
        a.setDueDate(request.getDueDate());
        a.setCreatedBy(branchScope.currentUserId());
        a = actionRepo.save(a);
        auditService.recordUserEvent("ClaimAction", a.getId(), jobCard.getBranchId(),
            EventType.CREATED, branchScope.currentUserId(),
            Map.of("jobCardId", jobCard.getId(), "dueDate", a.getDueDate().toString()));
        log.info("Claim action opened: orgId={} jobCardId={} due={} by={}",
            orgId, jobCard.getId(), a.getDueDate(), branchScope.currentUserId());
        return toResponse(orgId, a, jobCard, false);
    }

    @Transactional
    public ClaimActionResponse complete(Long id) {
        Long orgId = TenantContext.requireOrgId();
        ClaimAction a = actionRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Claim action", id));
        final Long jobCardId = a.getJobCardId();
        JobCard jobCard = jobCardRepo.findByIdAndOrgId(jobCardId, orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", jobCardId));
        a.setDoneAt(java.time.Instant.now());
        a = actionRepo.save(a);
        auditService.recordUserEvent("ClaimAction", a.getId(), jobCard.getBranchId(),
            EventType.CLOSED, branchScope.currentUserId(), Map.of("jobCardId", jobCard.getId()));
        log.info("Claim action completed: orgId={} actionId={} by={}", orgId, id, branchScope.currentUserId());
        return toResponse(orgId, a, jobCard, false);
    }

    private ClaimActionResponse toResponse(Long orgId, ClaimAction a, JobCard jobCard, boolean overdue) {
        String branchCode = branchRepo.findByIdAndOrgId(jobCard.getBranchId(), orgId)
            .map(b -> b.getCode()).orElse("?");
        String ownerName = null;
        if (a.getOwnerUserId() != null) {
            final Long ownerId = a.getOwnerUserId();
            ownerName = userRepo.findNameByIdAndOrganization_Id(ownerId, orgId).orElse(null);
        }
        return ClaimActionResponse.of(a, branchCode + "-JC-" + jobCard.getId(), ownerName, overdue);
    }
}
