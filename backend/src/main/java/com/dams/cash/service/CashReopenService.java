package com.dams.cash.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.dto.CreateReopenRequest;
import com.dams.cash.dto.ReopenRequestResponse;
import com.dams.cash.entity.CashCloseReopenRequest;
import com.dams.cash.entity.CashDayClose;
import com.dams.cash.entity.ReopenRequestStatus;
import com.dams.cash.repository.CashCloseReopenRequestRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cash-close reopen requests. A locked day is never reopened silently: the cashier files a
 * request (reason mandatory), and only a Finance Manager decision changes anything —
 * approval deletes the {@link CashDayClose} row so the day can be re-closed, rejection
 * just records why. Every step is audited, and maker-checker holds (the FM deciding must
 * differ from the cashier who asked).
 */
@Service
public class CashReopenService {

    private static final Logger log = LoggerFactory.getLogger(CashReopenService.class);

    private final CashCloseReopenRequestRepository reopenRepo;
    private final CashDayCloseRepository cashDayCloseRepo;
    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final BranchScope branchScope;
    private final CashPostingGuard postingGuard;
    private final AuditService auditService;

    public CashReopenService(CashCloseReopenRequestRepository reopenRepo,
                             CashDayCloseRepository cashDayCloseRepo,
                             BranchRepository branchRepo,
                             AppUserRepository userRepo,
                             BranchScope branchScope,
                             CashPostingGuard postingGuard,
                             AuditService auditService) {
        this.reopenRepo = reopenRepo;
        this.cashDayCloseRepo = cashDayCloseRepo;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.branchScope = branchScope;
        this.postingGuard = postingGuard;
        this.auditService = auditService;
    }

    /** Cashier files a request for their own home branch. */
    @Transactional
    public ReopenRequestResponse request(CreateReopenRequest request) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = postingGuard.requireCashier(orgId);
        Long branchId = me.getHomeBranchId();

        if (request.getCloseDate().isAfter(OrgTime.today())) {
            throw DamsException.badRequest("Cannot request a reopen for a future date ("
                + request.getCloseDate() + ")");
        }
        cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(orgId, branchId, request.getCloseDate())
            .orElseThrow(() -> DamsException.conflict("Cash for branch "
                + branchCode(orgId, branchId) + " on " + request.getCloseDate()
                + " is not closed — there is nothing to reopen"));
        if (reopenRepo.existsByOrgIdAndBranchIdAndCloseDateAndStatus(
                orgId, branchId, request.getCloseDate(), ReopenRequestStatus.PENDING)) {
            throw DamsException.conflict("A reopen request for branch " + branchCode(orgId, branchId)
                + " on " + request.getCloseDate() + " is already pending");
        }

        CashCloseReopenRequest row = new CashCloseReopenRequest();
        row.setOrgId(orgId);
        row.setBranchId(branchId);
        row.setCloseDate(request.getCloseDate());
        row.setReason(request.getReason().trim());
        row.setStatus(ReopenRequestStatus.PENDING);
        row.setRequestedBy(me.getId());
        row = reopenRepo.save(row);

        auditService.recordUserEvent("CashCloseReopenRequest", row.getId(), branchId,
            EventType.CREATED, me.getId(),
            detail("closeDate", request.getCloseDate().toString(), "reason", row.getReason()));
        log.info("Cash reopen requested: orgId={} branchId={} date={} requestId={} by={}",
            orgId, branchId, request.getCloseDate(), row.getId(), me.getId());
        return ReopenRequestResponse.of(row, branchCode(orgId, branchId));
    }

    /** Accountant / FM / Owner list — limited to the caller's branch scope. */
    @Transactional(readOnly = true)
    public List<ReopenRequestResponse> list(Long requestedBranchId, ReopenRequestStatus status) {
        Long orgId = TenantContext.requireOrgId();
        me(orgId);
        Optional<Set<Long>> allowed = branchScope.allowedBranchIds();

        List<CashCloseReopenRequest> rows = status == null
            ? reopenRepo.findByOrgIdOrderByCreatedAtDesc(orgId)
            : reopenRepo.findByOrgIdAndStatusOrderByCreatedAtDesc(orgId, status);
        List<ReopenRequestResponse> out = new ArrayList<>();
        for (CashCloseReopenRequest r : rows) {
            if (requestedBranchId != null && !requestedBranchId.equals(r.getBranchId())) {
                continue;
            }
            if (allowed.isPresent() && !allowed.get().contains(r.getBranchId())) {
                continue;
            }
            out.add(ReopenRequestResponse.of(r, branchCode(orgId, r.getBranchId())));
        }
        return out;
    }

    /** FM approves — the close row is deleted so the day can be re-closed, and both facts are audited. */
    @Transactional
    public ReopenRequestResponse approve(Long id) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = requireFinanceManager(orgId);
        CashCloseReopenRequest row = load(orgId, id);
        requirePending(row);
        requireBranchVisible(row);
        requireNotOwnRequest(me, row);

        CashDayClose close = cashDayCloseRepo
            .findByOrgIdAndBranchIdAndCloseDate(orgId, row.getBranchId(), row.getCloseDate())
            .orElseThrow(() -> DamsException.conflict("Cash close for branch "
                + branchCode(orgId, row.getBranchId()) + " on " + row.getCloseDate()
                + " no longer exists — it was already reopened"));
        Long closeId = close.getId();
        cashDayCloseRepo.delete(close);

        row.setStatus(ReopenRequestStatus.APPROVED);
        row.setDecidedBy(me.getId());
        row.setDecidedAt(Instant.now());
        reopenRepo.save(row);

        // Two audit facts: the request's approval, and the close's removal. A reopen must
        // always be traceable back to who allowed it — there is no silent path.
        auditService.recordUserEvent("CashCloseReopenRequest", row.getId(), row.getBranchId(),
            EventType.APPROVED, me.getId(), detail("closeDate", row.getCloseDate().toString()));
        auditService.recordUserEvent("CashDayClose", closeId, row.getBranchId(),
            EventType.CLOSED, me.getId(),
            detail("closeDate", row.getCloseDate().toString(), "reopened", true,
                "reopenRequestId", row.getId()));
        log.info("Cash reopen approved: orgId={} branchId={} date={} requestId={} closeId={} by={}",
            orgId, row.getBranchId(), row.getCloseDate(), row.getId(), closeId, me.getId());
        return ReopenRequestResponse.of(row, branchCode(orgId, row.getBranchId()));
    }

    /** FM rejects — the lock stays, and the reason is kept for the cashier. */
    @Transactional
    public ReopenRequestResponse reject(Long id, String reason) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = requireFinanceManager(orgId);
        CashCloseReopenRequest row = load(orgId, id);
        requirePending(row);
        requireBranchVisible(row);
        requireNotOwnRequest(me, row);

        row.setStatus(ReopenRequestStatus.REJECTED);
        row.setDecidedBy(me.getId());
        row.setDecidedAt(Instant.now());
        row.setDecisionNote(reason.trim());
        reopenRepo.save(row);

        auditService.recordUserEvent("CashCloseReopenRequest", row.getId(), row.getBranchId(),
            EventType.REJECTED, me.getId(),
            detail("closeDate", row.getCloseDate().toString(), "reason", row.getDecisionNote()));
        log.info("Cash reopen rejected: orgId={} branchId={} date={} requestId={} by={}",
            orgId, row.getBranchId(), row.getCloseDate(), row.getId(), me.getId());
        return ReopenRequestResponse.of(row, branchCode(orgId, row.getBranchId()));
    }

    // --- internals ---

    private AppUser me(Long orgId) {
        return userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));
    }

    private AppUser requireFinanceManager(Long orgId) {
        AppUser me = me(orgId);
        if (me.getRole() != Role.FINANCE_MANAGER) {
            throw DamsException.forbidden("Only a Finance Manager can decide a cash reopen request");
        }
        return me;
    }

    private CashCloseReopenRequest load(Long orgId, Long id) {
        return reopenRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Cash reopen request", id));
    }

    private void requirePending(CashCloseReopenRequest row) {
        if (row.getStatus() != ReopenRequestStatus.PENDING) {
            throw DamsException.conflict("Cash reopen request #" + row.getId()
                + " is already " + row.getStatus());
        }
    }

    private void requireBranchVisible(CashCloseReopenRequest row) {
        if (!branchScope.canSeeBranch(row.getBranchId())) {
            throw DamsException.forbidden("You do not have access to the branch of reopen request #"
                + row.getId());
        }
    }

    private static void requireNotOwnRequest(AppUser me, CashCloseReopenRequest row) {
        if (me.getId().equals(row.getRequestedBy())) {
            throw DamsException.conflict("You cannot decide cash reopen request #" + row.getId()
                + " because you requested it — a different Finance Manager must.");
        }
    }

    private String branchCode(Long orgId, Long branchId) {
        return branchRepo.findByIdAndOrgId(branchId, orgId).map(Branch::getCode).orElse("?");
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
