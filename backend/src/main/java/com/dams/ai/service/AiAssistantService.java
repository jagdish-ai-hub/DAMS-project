package com.dams.ai.service;

import com.dams.ai.dto.AiAssistantDtos.AiAnswer;
import com.dams.ai.dto.AiAssistantDtos.AiBrief;
import com.dams.ai.dto.AiAssistantDtos.BenchmarkNarrative;
import com.dams.ai.dto.AiOpsDtos.ClaimInsight;
import com.dams.ai.entity.AiQueryLog;
import com.dams.ai.repository.AiQueryLogRepository;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.dashboard.dto.ActivityItem;
import com.dams.dashboard.dto.BranchComparisonRow;
import com.dams.dashboard.dto.DashboardSummary;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.service.DashboardService;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The scoped Owner/Admin assistant (FEAT-09 Ask DAMS, FEAT-10 morning brief,
 * FEAT-14 benchmark narrator).
 *
 * Grounded 100% in database queries over the caller's organization. Supports:
 * - Documents: Receive (R), Expense (E), Cash (C)
 * - Job cards: {branchCode}-JC-{id} / JC-{id}
 * - Real operations: Claims, drawer cash, query roots, attention priorities, branch metrics.
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    // Human-readable numbers like OOR-JUL26-R-021 / OOR-JUL26-E-005 / OOR-JUL26-C-005.
    private static final Pattern DOC_NO =
        Pattern.compile("\\b([A-Z]{2,6}-[A-Z]{3}\\d{2}-[REC]-\\d{1,4})\\b", Pattern.CASE_INSENSITIVE);
    // Job card numbers like OOJ-JC-7 / OOR-JC-1 / JC-7.
    private static final Pattern JC_NO =
        Pattern.compile("\\b([A-Z]{2,6}-JC-(\\d{1,6})|JC-(\\d{1,6}))\\b", Pattern.CASE_INSENSITIVE);

    private final DashboardService dashboardService;
    private final BranchScope branchScope;
    private final AiQueryLogRepository queryLogRepo;
    private final InsightService insightService;
    private final AiOpsService opsService;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final BranchRepository branchRepo;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final ReceiveCategoryRepository receiveCategoryRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final AiWatchdogService watchdogService;

    public AiAssistantService(DashboardService dashboardService,
                              BranchScope branchScope,
                              AiQueryLogRepository queryLogRepo,
                              InsightService insightService,
                              AiOpsService opsService,
                              ReceiveDocumentRepository receiveDocumentRepo,
                              ExpenseDocumentRepository expenseDocumentRepo,
                              SettlementLineRepository settlementLineRepo,
                              ExpenseLineRepository expenseLineRepo,
                              BranchRepository branchRepo,
                              JobCardRepository jobCardRepo,
                              CustomerRepository customerRepo,
                              VehicleRepository vehicleRepo,
                              ReceiveCategoryRepository receiveCategoryRepo,
                              ClaimCloseRepository claimCloseRepo,
                              CashDocumentRepository cashDocumentRepo,
                              AiWatchdogService watchdogService) {
        this.dashboardService = dashboardService;
        this.branchScope = branchScope;
        this.queryLogRepo = queryLogRepo;
        this.insightService = insightService;
        this.opsService = opsService;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.branchRepo = branchRepo;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.receiveCategoryRepo = receiveCategoryRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.watchdogService = watchdogService;
    }

    /** Grounded natural-language answer over this org only (FEAT-09). */
    @Transactional
    public AiAnswer ask(String rawQuestion, Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isEmpty()) {
            throw DamsException.badRequest("Question must not be empty");
        }
        if (question.length() > 500) {
            throw DamsException.badRequest("Question is too long (max 500 characters)");
        }
        if (branchId != null && !branchScope.canSeeBranch(branchId)) {
            throw DamsException.forbidden("Branch " + branchId + " is outside your access");
        }

        // H2/H3/L6: resolve a named document first so a doc question never pays
        // for the aggregates — and a miss cites nothing instead of a phantom number.
        DocAnswer docAnswer = lookupDocument(orgId, branchId, question);
        final String facts;
        final List<String> citedDocs;
        if (docAnswer != null) {
            facts = docAnswer.facts();
            citedDocs = docAnswer.documentNo() != null ? List.of(docAnswer.documentNo()) : List.of();
        } else {
            DashboardSummary summary = dashboardService.summary(branchId, "mtd");
            List<OutstandingItem> outstanding = dashboardService.outstanding(branchId);
            facts = buildFacts(orgId, branchId, question, summary, outstanding);
            citedDocs = collectCitedDocs(orgId, branchId, question, summary, outstanding);
        }
        String answer = insightService.phraseAnswer(question, facts);

        String requestId = MDC.get("requestId");
        AiQueryLog row = new AiQueryLog();
        row.setOrgId(orgId);
        row.setUserId(branchScope.currentUserId());
        row.setQuestion(question);
        row.setCitedDocs(String.join(",", citedDocs.stream().limit(10).toList()));
        row.setRequestId(requestId);
        queryLogRepo.save(row);

        log.info("AI ask answered: orgId={} branchId={} citedDocs={} requestId={}",
            orgId, branchId, citedDocs.size(), requestId);
        return new AiAnswer(answer, citedDocs, requestId);
    }

    /** Five-bullet morning brief for the Owner (FEAT-10). */
    @Transactional(readOnly = true)
    public AiBrief brief(String period, Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        String safePeriod = "today".equals(period) ? "today" : "mtd";
        if (branchId != null && !branchScope.canSeeBranch(branchId)) {
            throw DamsException.forbidden("Branch " + branchId + " is outside your access");
        }
        DashboardSummary summary = dashboardService.summary(branchId, safePeriod);
        List<OutstandingItem> outstanding = dashboardService.outstanding(branchId);
        List<String> bullets = buildBriefBullets(branchId, summary, outstanding);
        log.info("AI brief built: orgId={} branchId={} period={} bullets={}",
            orgId, branchId, safePeriod, bullets.size());
        return new AiBrief(
            safePeriod, summary.scope(),
            summary.kpis().collections(), summary.kpis().expenses(),
            summary.kpis().net(), summary.kpis().cashInHand(),
            summary.kpis().pendingReview(), bullets);
    }

    /** Plain-English branch comparison, best collections first (FEAT-14). */
    @Transactional(readOnly = true)
    public BenchmarkNarrative benchmark() {
        Long orgId = TenantContext.requireOrgId();
        DashboardSummary summary = dashboardService.summary(null, "mtd");
        List<BranchComparisonRow> rows = new ArrayList<>(summary.branchComparison());
        rows.sort(Comparator.comparing(BranchComparisonRow::collections).reversed());

        List<String> lines = new ArrayList<>();
        for (BranchComparisonRow row : rows) {
            String line = row.branchCode() + ": collections " + row.collections()
                + ", expenses " + row.expenses() + ", net " + row.net()
                + ", cash in hand " + row.cashInHand()
                + ", " + row.pendingReview() + " pending review"
                + varianceNote(row);
            lines.add(line);
        }
        String headline = rows.isEmpty()
            ? "No branches with activity this month."
            : rows.get(0).branchCode() + " leads on collections this month.";
        log.info("AI benchmark built: orgId={} branches={}", orgId, rows.size());
        return new BenchmarkNarrative(lines, headline);
    }

    // --- internals: grounded database queries ---

    private record DocAnswer(String documentNo, String facts) {
    }

    private DocAnswer lookupDocument(Long orgId, Long branchId, String question) {
        // 1. Check Job Card reference (e.g. OOJ-JC-7, OOR-JC-1, JC-7)
        Matcher jcMatcher = JC_NO.matcher(question.toUpperCase());
        if (jcMatcher.find()) {
            String idStr = jcMatcher.group(2) != null ? jcMatcher.group(2) : jcMatcher.group(3);
            if (idStr != null) {
                try {
                    Long jcId = Long.parseLong(idStr);
                    var jcOpt = jobCardRepo.findByIdAndOrgId(jcId, orgId);
                    if (jcOpt.isPresent()) {
                        JobCard jc = jcOpt.get();
                        if (branchScope.canSeeBranch(jc.getBranchId()) && (branchId == null || branchId.equals(jc.getBranchId()))) {
                            String branchCode = branchCode(orgId, jc.getBranchId());
                            String jcRef = branchCode + "-JC-" + jc.getId();
                            return new DocAnswer(jcRef, jobCardFacts(orgId, jc));
                        }
                    }
                    return new DocAnswer(null, "No job card " + jcMatcher.group(1) + " found in your scope. Check the ID or branch filter.");
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 2. Check Document number (e.g. OOR-JUL26-R-021, OOR-JUL26-E-005, OOR-JUL26-C-005)
        Matcher matcher = DOC_NO.matcher(question.toUpperCase());
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1).toUpperCase();

        // Cash documents: {branch}-{MMMYY}-C-{seq}
        if (token.contains("-C-")) {
            var cashOpt = cashDocumentRepo.findByOrgIdAndDocumentNoIgnoreCase(orgId, token);
            if (cashOpt.isPresent()) {
                CashDocument doc = cashOpt.get();
                if (branchScope.canSeeBranch(doc.getBranchId()) && (branchId == null || branchId.equals(doc.getBranchId()))) {
                    return new DocAnswer(doc.getDocumentNo(), cashFacts(orgId, doc));
                }
            }
            return new DocAnswer(null, "No cash document " + token + " found in your scope. Check the number or branch filter.");
        }

        // Receive documents: {branch}-{MMMYY}-R-{seq}
        for (ReceiveDocument doc : receiveDocumentRepo.findByOrgIdAndDocumentNoContainingIgnoreCase(orgId, token)) {
            if (token.equalsIgnoreCase(doc.getDocumentNo())
                && branchScope.canSeeBranch(doc.getBranchId())
                && (branchId == null || branchId.equals(doc.getBranchId()))) {
                return new DocAnswer(doc.getDocumentNo(), receiptFacts(orgId, doc));
            }
        }

        // Expense documents: {branch}-{MMMYY}-E-{seq}
        for (ExpenseDocument doc : expenseDocumentRepo.findByOrgIdAndDocumentNoIgnoreCase(orgId, token)) {
            if (branchScope.canSeeBranch(doc.getBranchId())
                && (branchId == null || branchId.equals(doc.getBranchId()))) {
                return new DocAnswer(doc.getDocumentNo(), expenseFacts(orgId, doc));
            }
        }

        return new DocAnswer(null, "No receive or expense document " + token
            + " found in your scope. Check the number or your branch filter.");
    }

    private String jobCardFacts(Long orgId, JobCard jc) {
        String branch = branchCode(orgId, jc.getBranchId());
        String jcRef = branch + "-JC-" + jc.getId();
        String customer = customerRepo.findByIdAndOrgId(jc.getCustomerId(), orgId)
            .map(c -> c.getName()).orElse("Customer");
        String vehicle = jc.getVehicleId() != null
            ? vehicleRepo.findByIdAndOrgId(jc.getVehicleId(), orgId).map(v -> v.getVehicleNo()).orElse("—")
            : "—";
        String category = receiveCategoryRepo.findByIdAndOrgId(jc.getCategoryId(), orgId)
            .map(c -> c.getName()).orElse("General");
        boolean isClaim = receiveCategoryRepo.findByIdAndOrgId(jc.getCategoryId(), orgId)
            .map(c -> c.isClaim()).orElse(false);

        BigDecimal invoice = jc.getInvoiceAmount() != null ? jc.getInvoiceAmount() : BigDecimal.ZERO;
        List<ReceiveDocument> rDocs = receiveDocumentRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(orgId, jc.getId());
        BigDecimal totalReceived = BigDecimal.ZERO;
        for (ReceiveDocument rDoc : rDocs) {
            for (var line : settlementLineRepo.findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(orgId, rDoc.getId())) {
                totalReceived = totalReceived.add(line.getAmount());
            }
        }
        BigDecimal pending = invoice.subtract(totalReceived).max(BigDecimal.ZERO);

        StringBuilder sb = new StringBuilder();
        sb.append("Job Card ").append(jcRef).append(" at branch ").append(branch).append(": ")
            .append("Customer: ").append(customer).append(", Vehicle: ").append(vehicle).append(", Category: ").append(category).append(". ")
            .append("Invoice: ").append(jc.getInvoiceNo() != null ? jc.getInvoiceNo() : "—")
            .append(" (₹").append(invoice).append("), Total Received: ₹").append(totalReceived)
            .append(", Pending Balance: ₹").append(pending).append(". ");

        if (isClaim) {
            var closeOpt = claimCloseRepo.findByOrgIdAndJobCardId(orgId, jc.getId());
            if (closeOpt.isPresent()) {
                var close = closeOpt.get();
                sb.append("Claim Status: CLOSED · Final. Settled amount: ₹").append(close.getFinalAmount());
                if (close.isOverridden()) {
                    sb.append(" (Overridden by FM: ").append(close.getOverrideReason() != null ? close.getOverrideReason() : "—").append(")");
                }
                sb.append(".");
            } else {
                long days = Duration.between(jc.getCreatedAt(), Instant.now()).toDays();
                sb.append("Claim Status: OPEN (awaiting Eicher settlement, ").append(days).append(" days open).");
            }
        } else {
            sb.append(pending.signum() == 0 ? "Fully settled." : "Has outstanding balance.");
        }
        return sb.toString();
    }

    private String cashFacts(Long orgId, CashDocument doc) {
        return "Cash movement " + doc.getDocumentNo() + " at branch " + branchCode(orgId, doc.getBranchId())
            + ": direction " + doc.getDirection() + ", amount ₹" + doc.getAmount()
            + ", transaction date " + doc.getTransactionDate()
            + ", bankId: " + (doc.getBankId() != null ? doc.getBankId() : "—")
            + ", ref: " + (doc.getTransactionRef() != null ? doc.getTransactionRef() : "—")
            + ", workflow " + doc.getWorkflowStatus()
            + (doc.getRemark() != null ? ", remark: " + doc.getRemark() : "")
            + ". Internal cash movements affect drawer balance only (excluded from Collections and Expenses KPIs).";
    }

    private String receiptFacts(Long orgId, ReceiveDocument doc) {
        BigDecimal total = BigDecimal.ZERO;
        int lines = 0;
        for (var line : settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(orgId, doc.getId())) {
            total = total.add(line.getAmount());
            lines++;
        }
        return "Receipt " + doc.getDocumentNo() + " at branch " + branchCode(orgId, doc.getBranchId())
            + ": workflow " + doc.getWorkflowStatus().name()
            + (doc.isSettled() ? ", settled (pending amount is zero)"
                : ", still open (accepts new settlement lines until pending reaches zero)")
            + ", " + lines + " settlement lines totalling ₹" + total + "."
            + " Verification does not close receipts — only pending zero does.";
    }

    private String expenseFacts(Long orgId, ExpenseDocument doc) {
        BigDecimal total = BigDecimal.ZERO;
        int lines = 0;
        for (var line : expenseLineRepo
            .findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(orgId, doc.getId())) {
            total = total.add(line.getAmount());
            lines++;
        }
        return "Expense " + doc.getDocumentNo() + " at branch " + branchCode(orgId, doc.getBranchId())
            + ": workflow " + doc.getWorkflowStatus().name()
            + (doc.isOverLimit() ? ", OVER its sub-category limit" : ", within limit")
            + ", " + lines + " expense lines totalling ₹" + total + "."
            + " Expenses close only when the Accountant closes them explicitly.";
    }

    private String branchCode(Long orgId, Long branchId) {
        return branchRepo.findByIdAndOrgId(branchId, orgId)
            .map(branch -> branch.getCode())
            .orElse("?");
    }

    private String buildFacts(Long orgId, Long branchId, String question, DashboardSummary summary, List<OutstandingItem> outstanding) {
        StringBuilder facts = new StringBuilder();
        facts.append("Scope ").append(summary.scope()).append(", period ").append(summary.period()).append(". ");
        facts.append("Collections ₹").append(summary.kpis().collections())
            .append(", expenses ₹").append(summary.kpis().expenses())
            .append(", net ₹").append(summary.kpis().net())
            .append(", cash in hand ₹").append(summary.kpis().cashInHand())
            .append(", ").append(summary.kpis().pendingReview()).append(" pending review. ");

        String lower = question.toLowerCase();

        if (lower.contains("claim") || lower.contains("warranty") || lower.contains("amc")) {
            List<ClaimInsight> insights = opsService.claimInsights(branchId);
            if (!insights.isEmpty()) {
                long crit = insights.stream().filter(i -> "90+".equals(i.bucket())).count();
                long sixty = insights.stream().filter(i -> "60-90".equals(i.bucket())).count();
                facts.append("Open warranty/AMC claims (").append(insights.size()).append(" total, ")
                    .append(crit).append(" critical 90+ days, ").append(sixty).append(" 60-90 days): ");
                int count = 0;
                for (ClaimInsight i : insights) {
                    if (count++ >= 5) break;
                    facts.append(i.customerName()).append(" (").append(i.documentNo()).append(", ₹")
                        .append(i.amount()).append(", ").append(i.ageDays()).append(" days old); ");
                }
                if (insights.size() > 5) {
                    facts.append("and ").append(insights.size() - 5).append(" more. ");
                }
            } else {
                facts.append("No open warranty or AMC claims found in this scope. All claims are settled. ");
                List<com.dams.jobcard.entity.ClaimClose> recentClosed = claimCloseRepo.findByOrgIdOrderByClosedAtDesc(orgId, org.springframework.data.domain.Limit.of(2));
                if (!recentClosed.isEmpty()) {
                    facts.append("Recently closed: ");
                    for (var cc : recentClosed) {
                        facts.append("JC #").append(cc.getJobCardId()).append(" settled at ₹").append(cc.getFinalAmount()).append("; ");
                    }
                }
            }
        } else if (lower.contains("cash") || lower.contains("variance") || lower.contains("drawer")) {
            facts.append("Cash drawer status: ");
            boolean anyVariance = false;
            for (BranchComparisonRow row : summary.branchComparison()) {
                facts.append(row.branchCode()).append(" cash in hand: ₹").append(row.cashInHand());
                if (row.variance() != null && row.variance().signum() != 0) {
                    facts.append(" (ALERT: variance ₹").append(row.variance()).append(")");
                    anyVariance = true;
                }
                facts.append("; ");
            }
            if (!anyVariance) {
                facts.append("All branch cash closings match physical counts with zero variance. ");
            }
            facts.append("Total cash in hand: ₹").append(summary.kpis().cashInHand()).append(". ");
        } else if (lower.contains("queried") || lower.contains("query") || lower.contains("reject")) {
            var roots = watchdogService.queryRoots(branchId);
            if (!roots.isEmpty()) {
                facts.append("Queried entries root causes from audit trail: ");
                for (var r : roots) {
                    facts.append(r.cause()).append(" (").append(r.count()).append(" times, sample: '").append(r.suggestion()).append("'); ");
                }
            } else {
                facts.append("No entries have been queried in this scope. All review entries proceeded without queries. ");
            }
        } else if (lower.contains("attention") || lower.contains("priority") || lower.contains("priorities") || lower.contains("focus") || lower.contains("urgent")) {
            facts.append("Operational priorities needing attention: ");
            facts.append("1) Review queue: ").append(summary.kpis().pendingReview()).append(" entries awaiting verification/approval. ");
            List<ClaimInsight> claims = opsService.claimInsights(branchId);
            long crit = claims.stream().filter(c -> "90+".equals(c.bucket())).count();
            if (crit > 0) {
                facts.append("2) Aging claims: ").append(crit).append(" claims over 90 days awaiting Eicher settlement. ");
            }
            boolean variance = summary.branchComparison().stream().anyMatch(r -> r.variance() != null && r.variance().signum() != 0);
            if (variance) {
                facts.append("3) Cash variance detected on recent close — review physical count remark. ");
            }
        } else if (lower.contains("branch") || lower.contains("compare") || lower.contains("best") || lower.contains("performance")) {
            facts.append("Branch performance comparison: ");
            for (BranchComparisonRow row : summary.branchComparison()) {
                facts.append(row.branchCode()).append(": collections ₹").append(row.collections())
                    .append(", expenses ₹").append(row.expenses())
                    .append(", net ₹").append(row.net())
                    .append(", cash in hand ₹").append(row.cashInHand())
                    .append(", ").append(row.pendingReview()).append(" pending review. ");
            }
        } else if (lower.contains("pending") || lower.contains("owed") || lower.contains("outstanding")
            || lower.contains("unpaid") || lower.contains("due")) {
            appendOutstandingKind(facts, outstanding, null, "Outstanding");
        } else {
            appendOutstandingKind(facts, outstanding, null, "Largest outstanding");
        }
        return facts.toString().trim();
    }

    private void appendOutstandingKind(StringBuilder facts, List<OutstandingItem> outstanding,
                                       String kind, String label) {
        List<OutstandingItem> matching = new ArrayList<>();
        for (OutstandingItem item : outstanding) {
            if (kind == null || kind.equals(item.kind())) {
                matching.add(item);
            }
        }
        if (matching.isEmpty()) {
            facts.append("No ").append(label.toLowerCase()).append(" items. ");
            return;
        }
        facts.append(label).append(": ");
        int shown = 0;
        for (OutstandingItem item : matching) {
            if (shown >= 5) {
                break;
            }
            facts.append(item.name()).append(" (").append(item.documentNo())
                .append(", ₹").append(item.amount()).append(") ");
            shown++;
        }
        if (matching.size() > shown) {
            facts.append("and ").append(matching.size() - shown).append(" more. ");
        }
    }

    private List<String> collectCitedDocs(Long orgId, Long branchId, String question,
                                          DashboardSummary summary, List<OutstandingItem> outstanding) {
        List<String> docs = new ArrayList<>();
        String lower = question.toLowerCase();

        if (lower.contains("claim") || lower.contains("warranty") || lower.contains("amc")) {
            for (ClaimInsight i : opsService.claimInsights(branchId)) {
                if (i.documentNo() != null && !docs.contains(i.documentNo()) && docs.size() < 10) {
                    docs.add(i.documentNo());
                }
            }
        }

        for (OutstandingItem item : outstanding) {
            if (item.documentNo() != null && !item.documentNo().isBlank() && !docs.contains(item.documentNo()) && docs.size() < 10) {
                docs.add(item.documentNo());
            }
        }
        return docs;
    }

    private List<String> buildBriefBullets(Long branchId, DashboardSummary summary,
                                           List<OutstandingItem> outstanding) {
        List<String> bullets = new ArrayList<>();
        bullets.add("Collections ₹" + summary.kpis().collections()
            + " vs expenses ₹" + summary.kpis().expenses()
            + " (net ₹" + summary.kpis().net() + ", cash In/Out excluded).");
        bullets.add("Cash in hand ₹" + summary.kpis().cashInHand()
            + " with " + summary.kpis().pendingReview() + " entries pending review.");
        long claims = 0;
        for (OutstandingItem item : outstanding) {
            if ("claim".equals(item.kind())) {
                claims++;
            }
        }
        long critical = 0;
        for (ClaimInsight insight : opsService.claimInsights(branchId)) {
            if ("90+".equals(insight.bucket())) {
                critical++;
            }
        }
        bullets.add(claims + " open warranty/AMC claims"
            + (critical > 0 ? " (" + critical + " critical 90+ days)" : "") + ".");
        List<ActivityItem> activity = dashboardService.activity(branchId, 5);
        if (activity.isEmpty()) {
            bullets.add("No recent activity.");
        } else {
            ActivityItem latest = activity.get(0);
            bullets.add("Latest: " + latest.action() + " " + latest.documentNo()
                + " by " + latest.actor() + ".");
        }
        bullets.add(insightService.phraseBrief("Scope " + summary.scope() + ", period " + summary.period() + "."));
        return bullets;
    }

    private String varianceNote(BranchComparisonRow row) {
        if (row.variance() != null && row.variance().signum() != 0) {
            return ", last close variance ₹" + row.variance();
        }
        return "";
    }
}
