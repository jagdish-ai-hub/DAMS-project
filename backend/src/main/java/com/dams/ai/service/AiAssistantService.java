package com.dams.ai.service;

import com.dams.ai.dto.AiAssistantDtos.AiAnswer;
import com.dams.ai.dto.AiAssistantDtos.AiBrief;
import com.dams.ai.dto.AiAssistantDtos.BenchmarkNarrative;
import com.dams.ai.dto.AiOpsDtos.ClaimInsight;
import com.dams.ai.entity.AiQueryLog;
import com.dams.ai.repository.AiQueryLogRepository;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.dashboard.dto.ActivityItem;
import com.dams.dashboard.dto.BranchComparisonRow;
import com.dams.dashboard.dto.DashboardSummary;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.service.DashboardService;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The scoped Owner/Admin assistant (FEAT-09 Ask DAMS, FEAT-10 morning brief,
 * FEAT-14 benchmark narrator).
 *
 * Read-only by construction: every number comes from {@link DashboardService} /
 * {@link SearchService}, which already enforce {@code org_id} + branch scope.
 * The only write in the whole AI module is the {@code ai_query_log} trace row.
 * Owner stays read-only on transactions — answers suggest, humans still click.
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    // Human-readable numbers like OOR-JUL26-R-021 / OOR-JUL26-E-005 / OOR-JUL26-C-005.
    private static final Pattern DOC_NO =
        Pattern.compile("\\b([A-Z]{2,6}-[A-Z]{3}\\d{2}-[REC]-\\d{1,4})\\b", Pattern.CASE_INSENSITIVE);

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

    public AiAssistantService(DashboardService dashboardService,
                              BranchScope branchScope,
                              AiQueryLogRepository queryLogRepo,
                              InsightService insightService,
                              AiOpsService opsService,
                              ReceiveDocumentRepository receiveDocumentRepo,
                              ExpenseDocumentRepository expenseDocumentRepo,
                              SettlementLineRepository settlementLineRepo,
                              ExpenseLineRepository expenseLineRepo,
                              BranchRepository branchRepo) {
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
            facts = buildFacts(question, summary, outstanding);
            citedDocs = collectCitedDocs(summary, outstanding);
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
            // BranchComparisonRow is one branch's verified line — narrate, never recompute money.
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

    // --- internals: plain loops, no clever streams ---

    private record DocAnswer(String documentNo, String facts) {
    }

    /**
     * Resolves a document number mentioned in the question to grounded facts, or
     * null when the question names no document. A number outside the caller's
     * branch scope — or outside the requested branch filter — reads as "not
     * found" with a null citation, so existence itself must not leak and a miss
     * never cites a phantom number (H2/H3).
     */
    private DocAnswer lookupDocument(Long orgId, Long branchId, String question) {
        Matcher matcher = DOC_NO.matcher(question.toUpperCase());
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1).toUpperCase();
        for (ReceiveDocument doc : receiveDocumentRepo
            .findByOrgIdAndDocumentNoContainingIgnoreCase(orgId, token)) {
            if (token.equalsIgnoreCase(doc.getDocumentNo())
                && branchScope.canSeeBranch(doc.getBranchId())
                && (branchId == null || branchId.equals(doc.getBranchId()))) {
                return new DocAnswer(doc.getDocumentNo(), receiptFacts(orgId, doc));
            }
        }
        for (ExpenseDocument doc : expenseDocumentRepo
            .findByOrgIdAndDocumentNoIgnoreCase(orgId, token)) {
            if (branchScope.canSeeBranch(doc.getBranchId())
                && (branchId == null || branchId.equals(doc.getBranchId()))) {
                return new DocAnswer(doc.getDocumentNo(), expenseFacts(orgId, doc));
            }
        }
        // Cash documents have no lines to summarise; report presence only.
        return new DocAnswer(null, "No receive or expense document " + token
            + " found in your scope. Check the number or your branch filter.");
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
            + ", " + lines + " settlement lines totalling " + total + "."
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
            + ", " + lines + " expense lines totalling " + total + "."
            + " Expenses close only when the Accountant closes them explicitly.";
    }

    private String branchCode(Long orgId, Long branchId) {
        return branchRepo.findByIdAndOrgId(branchId, orgId)
            .map(branch -> branch.getCode())
            .orElse("?");
    }

    private String buildFacts(String question, DashboardSummary summary, List<OutstandingItem> outstanding) {
        StringBuilder facts = new StringBuilder();
        facts.append("Scope ").append(summary.scope()).append(", period ").append(summary.period()).append(". ");
        facts.append("Collections ").append(summary.kpis().collections())
            .append(", expenses ").append(summary.kpis().expenses())
            .append(", net ").append(summary.kpis().net())
            .append(", cash in hand ").append(summary.kpis().cashInHand())
            .append(", ").append(summary.kpis().pendingReview()).append(" pending review. ");
        String lower = question.toLowerCase();
        if (lower.contains("claim") || lower.contains("warranty") || lower.contains("amc")) {
            appendOutstandingKind(facts, outstanding, "claim", "Open claims");
        } else if (lower.contains("pending") || lower.contains("owed") || lower.contains("outstanding")
            || lower.contains("unpaid") || lower.contains("due")) {
            appendOutstandingKind(facts, outstanding, null, "Outstanding");
        } else if (lower.contains("branch") || lower.contains("compare") || lower.contains("best")) {
            for (BranchComparisonRow row : summary.branchComparison()) {
                facts.append(row.branchCode()).append(" collections ").append(row.collections())
                    .append ", net ".append(row.net()).append(". ");
            }
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
                .append(", ").append(item.amount()).append(") ");
            shown++;
        }
        if (matching.size() > shown) {
            facts.append("and ").append(matching.size() - shown).append(" more. ");
        }
    }

    private List<String> collectCitedDocs(DashboardSummary summary, List<OutstandingItem> outstanding) {
        List<String> docs = new ArrayList<>();
        for (OutstandingItem item : outstanding) {
            // Only cite real document numbers the aggregates returned — never invent any.
            if (item.documentNo() != null && !item.documentNo().isBlank() && docs.size() < 10) {
                docs.add(item.documentNo());
            }
        }
        return docs;
    }

    private List<String> buildBriefBullets(Long branchId, DashboardSummary summary,
                                           List<OutstandingItem> outstanding) {
        List<String> bullets = new ArrayList<>();
        bullets.add("Collections " + summary.kpis().collections()
            + " vs expenses " + summary.kpis().expenses()
            + " (net " + summary.kpis().net() + ", cash In/Out excluded).");
        bullets.add("Cash in hand " + summary.kpis().cashInHand()
            + " with " + summary.kpis().pendingReview() + " entries pending review.");
        long claims = 0;
        for (OutstandingItem item : outstanding) {
            if ("claim".equals(item.kind())) {
                claims++;
            }
        }
        // H1: claim age lives on the job card, not in the outstanding line — count
        // real 90+ buckets from the claim insights instead of string-sniffing.
        long critical = 0;
        for (ClaimInsight insight : opsService.claimInsights(branchId)) {
            if ("90+".equals(insight.bucket())) {
                critical++;
            }
        }
        bullets.add(claims + " open warranty/AMC claims"
            + (critical > 0 ? " (" + critical + " critical 90+ days)" : "") + ".");
        // H4: latest activity honours the same branch filter as everything else.
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
        // A non-zero close variance is the one comparison-table signal worth narrating.
        if (row.variance() != null && row.variance().signum() != 0) {
            return ", last close variance " + row.variance();
        }
        return "";
    }
}
