package com.dams.followup.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.followup.dto.CreateFollowupRequest;
import com.dams.followup.dto.DefaulterRow;
import com.dams.followup.dto.FollowupResponse;
import com.dams.followup.entity.CreditFollowup;
import com.dams.followup.repository.CreditFollowupRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.repository.ReceiveDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collection follow-ups (FEAT-35): who owes, by when, what was promised.
 * The dashboard outstanding list shows balances; this owns the next step.
 * Overdue is derived at read time (live + due_date &lt; today) so it can
 * never go stale in storage. Branch visibility follows the caller's
 * BranchScope — an accountant only herds their own branches' dues.
 */
@Service
public class FollowupService {

    private static final Logger log = LoggerFactory.getLogger(FollowupService.class);

    private final CreditFollowupRepository followupRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final BranchRepository branchRepo;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final BranchScope branchScope;
    private final AuditService auditService;

    public FollowupService(CreditFollowupRepository followupRepo,
                           ReceiveDocumentRepository receiveDocumentRepo,
                           JobCardRepository jobCardRepo,
                           CustomerRepository customerRepo,
                           BranchRepository branchRepo,
                           PendingAmountCalculator pendingAmountCalculator,
                           BranchScope branchScope,
                           AuditService auditService) {
        this.followupRepo = followupRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.branchRepo = branchRepo;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.branchScope = branchScope;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FollowupResponse> list(boolean overdueOnly) {
        Long orgId = TenantContext.requireOrgId();
        LocalDate today = OrgTime.today();
        List<FollowupResponse> out = new ArrayList<>();
        for (CreditFollowup f : followupRepo
                .findByOrgIdAndStatusInOrderByDueDateAsc(orgId, List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED))) {
            DocContext ctx = loadDoc(orgId, f.getReceiveDocumentId());
            if (!branchScope.canSeeBranch(ctx.doc().getBranchId())) {
                continue;
            }
            boolean overdue = f.getDueDate().isBefore(today);
            if (overdueOnly && !overdue) {
                continue;
            }
            out.add(toResponse(orgId, f, ctx, overdue, today));
        }
        return out;
    }

    /** Customers ranked by outstanding across live follow-ups — the defaulter view. */
    @Transactional(readOnly = true)
    public List<DefaulterRow> defaulters() {
        Long orgId = TenantContext.requireOrgId();
        LocalDate today = OrgTime.today();
        Map<Long, Accumulator> byCustomer = new LinkedHashMap<>();
        for (CreditFollowup f : followupRepo
                .findByOrgIdAndStatusInOrderByDueDateAsc(orgId, List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED))) {
            DocContext ctx = loadDoc(orgId, f.getReceiveDocumentId());
            if (!branchScope.canSeeBranch(ctx.doc().getBranchId())) {
                continue;
            }
            boolean overdue = f.getDueDate().isBefore(today);
            long overdueDays = overdue ? ChronoUnit.DAYS.between(f.getDueDate(), today) : 0;
            BigDecimal pending = pendingFor(orgId, ctx.jobCard());
            Accumulator acc = byCustomer.computeIfAbsent(ctx.jobCard().getCustomerId(),
                id -> new Accumulator(ctx.customerName(), ctx.customerPhone()));
            acc.total = acc.total.add(pending);
            acc.open++;
            if (overdue) {
                acc.overdue++;
                acc.oldestOverdueDays = Math.max(acc.oldestOverdueDays, overdueDays);
            }
        }
        List<DefaulterRow> rows = new ArrayList<>();
        byCustomer.forEach((customerId, acc) -> rows.add(new DefaulterRow(
            customerId, acc.name, acc.phone, acc.total, acc.open, acc.overdue,
            acc.overdue > 0 ? acc.oldestOverdueDays : null)));
        rows.sort((a, b) -> b.totalOutstanding().compareTo(a.totalOutstanding()));
        return rows;
    }

    @Transactional
    public FollowupResponse open(CreateFollowupRequest request) {
        Long orgId = TenantContext.requireOrgId();
        DocContext ctx = loadDoc(orgId, request.getReceiveDocumentId());
        if (!branchScope.canSeeBranch(ctx.doc().getBranchId())) {
            throw DamsException.forbidden("You cannot follow up a document outside your branches");
        }
        if (request.getDueDate().isBefore(OrgTime.today())) {
            throw DamsException.badRequest("dueDate cannot be in the past — record the promise the customer actually made");
        }
        // Re-promising reuses the row so the reminder count and history survive.
        CreditFollowup f = followupRepo
            .findByOrgIdAndReceiveDocumentIdAndStatusIn(
                orgId, request.getReceiveDocumentId(), List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED))
            .orElseGet(() -> {
                CreditFollowup n = new CreditFollowup();
                n.setOrgId(orgId);
                n.setReceiveDocumentId(request.getReceiveDocumentId());
                n.setCreatedBy(branchScope.currentUserId());
                return n;
            });
        f.setDueDate(request.getDueDate());
        f.setPromiseNote(request.getPromiseNote() == null ? null : request.getPromiseNote().trim());
        f.setStatus(CreditFollowup.PROMISED);
        f = followupRepo.save(f);
        auditService.recordUserEvent("CreditFollowup", f.getId(), ctx.doc().getBranchId(),
            EventType.CREATED, branchScope.currentUserId(),
            Map.of("documentNo", ctx.doc().getDocumentNo(), "dueDate", f.getDueDate().toString()));
        log.info("Follow-up opened: orgId={} docId={} due={} by={}",
            orgId, ctx.doc().getId(), f.getDueDate(), branchScope.currentUserId());
        return toResponse(orgId, f, ctx, false, OrgTime.today());
    }

    @Transactional
    public FollowupResponse close(Long id) {
        Long orgId = TenantContext.requireOrgId();
        CreditFollowup f = followupRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Credit follow-up", id));
        DocContext ctx = loadDoc(orgId, f.getReceiveDocumentId());
        if (!branchScope.canSeeBranch(ctx.doc().getBranchId())) {
            throw DamsException.forbidden("You cannot close a follow-up outside your branches");
        }
        f.setStatus(CreditFollowup.CLOSED);
        f.setClosedAt(java.time.Instant.now());
        f = followupRepo.save(f);
        auditService.recordUserEvent("CreditFollowup", f.getId(), ctx.doc().getBranchId(),
            EventType.CLOSED, branchScope.currentUserId(), Map.of("documentNo", ctx.doc().getDocumentNo()));
        log.info("Follow-up closed: orgId={} followupId={} by={}", orgId, id, branchScope.currentUserId());
        return toResponse(orgId, f, ctx, false, OrgTime.today());
    }

    /** Called by the reminder sender — bumps the count so chasing is visible. */
    @Transactional
    public void markReminded(Long orgId, Long followupId) {
        CreditFollowup f = followupRepo.findByIdAndOrgId(followupId, orgId)
            .orElseThrow(() -> DamsException.notFound("Credit follow-up", followupId));
        f.setRemindedCount(f.getRemindedCount() + 1);
        f.setLastRemindedAt(java.time.Instant.now());
        followupRepo.save(f);
    }

    // ---------------------------------------------------------- internals

    private record DocContext(ReceiveDocument doc, JobCard jobCard, String customerName, String customerPhone) {
    }

    private static class Accumulator {
        final String name;
        final String phone;
        BigDecimal total = BigDecimal.ZERO;
        int open = 0;
        int overdue = 0;
        long oldestOverdueDays = 0;

        Accumulator(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    private DocContext loadDoc(Long orgId, Long receiveDocumentId) {
        ReceiveDocument doc = receiveDocumentRepo.findByIdAndOrgId(receiveDocumentId, orgId)
            .orElseThrow(() -> DamsException.notFound("Receive document", receiveDocumentId));
        JobCard jobCard = jobCardRepo.findByIdAndOrgId(doc.getJobCardId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Job card", doc.getJobCardId()));
        var customer = customerRepo.findByIdAndOrgId(jobCard.getCustomerId(), orgId).orElse(null);
        return new DocContext(doc, jobCard,
            customer == null ? "—" : customer.getName(),
            customer == null ? null : customer.getPhone());
    }

    private BigDecimal pendingFor(Long orgId, JobCard jobCard) {
        if (jobCard.getInvoiceAmount() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal pending = pendingAmountCalculator.forJobCard(orgId, jobCard.getId(), jobCard.getInvoiceAmount());
        return pending.signum() < 0 ? BigDecimal.ZERO : pending;
    }

    private FollowupResponse toResponse(Long orgId, CreditFollowup f, DocContext ctx,
                                        boolean overdue, LocalDate today) {
        long daysOverdue = overdue ? ChronoUnit.DAYS.between(f.getDueDate(), today) : 0;
        String branchCode = branchRepo.findByIdAndOrgId(ctx.doc().getBranchId(), orgId)
            .map(b -> b.getCode()).orElse("?");
        return FollowupResponse.of(f, ctx.doc().getDocumentNo(), ctx.customerName(), ctx.customerPhone(),
            ctx.doc().getBranchId(), branchCode, overdue, daysOverdue, pendingFor(orgId, ctx.jobCard()));
    }
}
