package com.dams.ai.service;

import com.dams.ai.dto.AiOpsDtos.CashAdvice;
import com.dams.ai.dto.AiOpsDtos.ClaimInsight;
import com.dams.ai.dto.AiOpsDtos.CloseChecklistRow;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.dashboard.dto.BranchComparisonRow;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.service.DashboardService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operations insights (FEAT-12 claim chaser, FEAT-15 cash advice, FEAT-21 close
 * checklist). Open claims are discovered the same way as the FM queue — an
 * APPROVED receipt on a job card carrying a Claim Type with no ClaimClose row — but
 * scoped here so the Owner sees them read-only without FM role guards.
 * Never closes anything: the FM still calls {@code POST /job-cards/{id}/close-claim}.
 */
@Service
public class AiOpsService {

    private static final Logger log = LoggerFactory.getLogger(AiOpsService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal HIGH_CASH = new BigDecimal("80000");

    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final JobCardRepository jobCardRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final CustomerRepository customerRepo;
    private final BranchRepository branchRepo;
    private final DashboardService dashboardService;
    private final BranchScope branchScope;

    public AiOpsService(ReceiveDocumentRepository receiveDocumentRepo,
                        JobCardRepository jobCardRepo,
                        ClaimCloseRepository claimCloseRepo,
                        CustomerRepository customerRepo,
                        BranchRepository branchRepo,
                        DashboardService dashboardService,
                        BranchScope branchScope) {
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.jobCardRepo = jobCardRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.customerRepo = customerRepo;
        this.branchRepo = branchRepo;
        this.dashboardService = dashboardService;
        this.branchScope = branchScope;
    }

    /** At-risk Warranty/AMC/CG claims, oldest first, each with a follow-up draft (FEAT-12). */
    @Transactional(readOnly = true)
    public List<ClaimInsight> claimInsights(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        requireVisibleBranch(branchId);

        Set<Long> closedJcIds = new HashSet<>(claimCloseRepo.findJobCardIdsByOrgId(orgId));
        List<ReceiveDocument> approved = receiveDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, WorkflowStatus.APPROVED);

        // One row per job card — a job card may own several documents over its life.
        // M2: resolve the allowed set once, not once per document.
        Optional<Set<Long>> allowed = branchScope.allowedBranchIds();
        Map<Long, List<ReceiveDocument>> docsByJobCard = new HashMap<>();
        for (ReceiveDocument doc : approved) {
            if (!visible(allowed, branchId, doc.getBranchId())
                || closedJcIds.contains(doc.getJobCardId())) {
                continue;
            }
            docsByJobCard.computeIfAbsent(doc.getJobCardId(), key -> new ArrayList<>()).add(doc);
        }
        if (docsByJobCard.isEmpty()) {
            return List.of();
        }
        Map<Long, JobCard> jobCards = new HashMap<>();
        for (JobCard jc : jobCardRepo.findByOrgIdAndIdIn(orgId, new ArrayList<>(docsByJobCard.keySet()))) {
            if (jc.getClaimTypeId() != null) {
                jobCards.put(jc.getId(), jc);
            }
        }
        Map<Long, String> customers = customerNames(orgId, jobCards.values().stream()
            .map(JobCard::getCustomerId).collect(Collectors.toSet()));
        Map<Long, String> codes = branchCodes(orgId);

        List<ClaimInsight> insights = new ArrayList<>();
        Instant now = Instant.now();
        for (Map.Entry<Long, JobCard> entry : jobCards.entrySet()) {
            JobCard jc = entry.getValue();
            List<ReceiveDocument> docs = docsByJobCard.get(entry.getKey());
            if (docs == null || docs.isEmpty()) {
                continue;
            }
            int ageDays = (int) Duration.between(jc.getCreatedAt(), now).toDays();
            String bucket = bucketFor(ageDays);
            BigDecimal amount = jc.getInvoiceAmount() != null ? jc.getInvoiceAmount() : BigDecimal.ZERO;
            // M8: the insight cites one primary number so "tell me about X" resolves
            // it exactly — the full history stays in the follow-up draft below.
            ReceiveDocument primary = docs.get(0);
            String primaryNo = primary.getDocumentNo() != null
                ? primary.getDocumentNo() : "#" + primary.getId();
            String docNos = docs.stream()
                .map(doc -> doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId())
                .collect(Collectors.joining(", "));
            String branchCode = codes.getOrDefault(jc.getBranchId(), "");
            insights.add(new ClaimInsight(primaryNo, branchCode,
                customers.getOrDefault(jc.getCustomerId(), "Customer"),
                amount, ageDays, bucket, draftFollowUp(branchCode, jc, amount, ageDays, docNos)));
        }
        insights.sort((left, right) -> Integer.compare(right.ageDays(), left.ageDays()));
        log.info("AI claim insights built: orgId={} branchId={} openClaims={}",
            orgId, branchId, insights.size());
        return insights;
    }

    /** Deposit timing + variance explanations per branch (FEAT-15). */
    @Transactional(readOnly = true)
    public List<CashAdvice> cashAdvice(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        requireVisibleBranch(branchId);
        List<CashAdvice> advice = new ArrayList<>();
        for (BranchComparisonRow row : dashboardService.summary(branchId, "mtd").branchComparison()) {
            if (row.variance() != null && row.variance().signum() != 0) {
                advice.add(new CashAdvice(row.branchCode(),
                    "Closed with variance " + row.variance()
                        + " — confirm the count remark before approving more closes.",
                    "urgent"));
            }
            if (row.lastClosed() == null) {
                advice.add(new CashAdvice(row.branchCode(),
                    "No cash close on record — confirm the cashier closes the drawer daily.",
                    "watch"));
            }
            if (row.cashInHand() != null && row.cashInHand().compareTo(HIGH_CASH) > 0) {
                advice.add(new CashAdvice(row.branchCode(),
                    "Holding " + row.cashInHand() + " in the drawer — move a Cash Out to bank soon.",
                    "watch"));
            }
        }
        if (advice.isEmpty()) {
            // M5: show the branch code, never the raw numeric id.
            String code = "ALL";
            if (branchId != null) {
                for (Branch branch : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
                    if (branchId.equals(branch.getId())) {
                        code = branch.getCode();
                    }
                }
            }
            advice.add(new CashAdvice(code,
                "Cash drawers look healthy — closes are on time with no variances.", "info"));
        }
        log.info("AI cash advice built: orgId={} branchId={} rows={}", orgId, branchId, advice.size());
        return advice;
    }

    /** Per-branch month-end sign-off checklist (FEAT-21). */
    @Transactional(readOnly = true)
    public List<CloseChecklistRow> closeChecklist(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        requireVisibleBranch(branchId);
        LocalDate today = LocalDate.now(IST);
        Map<String, Long> claimsByBranch = new HashMap<>();
        for (OutstandingItem item : dashboardService.outstanding(branchId)) {
            if ("claim".equals(item.kind())) {
                claimsByBranch.merge(item.branchCode(), 1L, Long::sum);
            }
        }
        List<CloseChecklistRow> rows = new ArrayList<>();
        for (BranchComparisonRow row : dashboardService.summary(branchId, "mtd").branchComparison()) {
            boolean cashClosed = row.lastClosed() != null && !row.lastClosed().isBefore(today);
            String cashNote = row.lastClosed() == null
                ? "No close on record"
                : "Last closed " + row.lastClosed();
            long openClaims = claimsByBranch.getOrDefault(row.branchCode(), 0L);
            // Open claims never block a close — they settle with the OEM on their own clock.
            boolean ready = cashClosed && row.pendingReview() == 0;
            rows.add(new CloseChecklistRow(row.branchCode(), cashClosed, cashNote,
                row.pendingReview(), openClaims, ready));
        }
        log.info("AI close checklist built: orgId={} branchId={} branches={}",
            orgId, branchId, rows.size());
        return rows;
    }

    // --- internals ---

    private String bucketFor(int ageDays) {
        if (ageDays <= 30) {
            return "0-30";
        }
        if (ageDays <= 60) {
            return "31-60";
        }
        if (ageDays <= 90) {
            return "61-90";
        }
        return "90+";
    }

    private String draftFollowUp(String branchCode, JobCard jc, BigDecimal amount,
                                 int ageDays, String docNos) {
        String ref = branchCode + "-JC-" + jc.getId()
            + (jc.getDbmId() != null ? " (DBM " + jc.getDbmId() + ")" : "");
        return "Dear OEM claims desk — following up on " + ref + ": claim of " + amount
            + " open " + ageDays + " days. DAMS documents: " + docNos + ".";
    }

    private Map<Long, String> customerNames(Long orgId, Set<Long> customerIds) {
        Map<Long, String> names = new HashMap<>();
        if (customerIds.isEmpty()) {
            return names;
        }
        for (Customer customer : customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, customerIds)) {
            names.put(customer.getId(), customer.getName());
        }
        return names;
    }

    private Map<Long, String> branchCodes(Long orgId) {
        Map<Long, String> codes = new HashMap<>();
        for (Branch branch : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
            codes.put(branch.getId(), branch.getCode());
        }
        return codes;
    }

    private void requireVisibleBranch(Long branchId) {
        if (branchId != null && !branchScope.canSeeBranch(branchId)) {
            throw DamsException.forbidden("Branch " + branchId + " is outside your access");
        }
    }

    private boolean visible(Optional<Set<Long>> allowed, Long filter, Long docBranchId) {
        if (filter != null && !filter.equals(docBranchId)) {
            return false;
        }
        return allowed.map(set -> set.contains(docBranchId)).orElse(true);
    }
}
