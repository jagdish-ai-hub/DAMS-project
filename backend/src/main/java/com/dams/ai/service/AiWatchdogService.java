package com.dams.ai.service;

import com.dams.ai.dto.AiWatchdogDtos.AnomalyItem;
import com.dams.ai.dto.AiWatchdogDtos.QueryRoot;
import com.dams.ai.dto.AiWatchdogDtos.RiskScore;
import com.dams.attachment.entity.ParentType;
import com.dams.attachment.repository.AttachmentRepository;
import com.dams.audit.dto.OverrideAuditEntry;
import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.audit.service.OverrideAuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.dashboard.dto.BranchComparisonRow;
import com.dams.dashboard.service.DashboardService;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Review-quality watchdog (FEAT-11 anomalies, FEAT-16 risk scores, FEAT-13
 * query roots). Deterministic rules over the override audit + SUBMITTED queues —
 * the language layer only explains flags, never invents them.
 *
 * Reads its own queries (org + branch scoped) instead of reusing ReviewService
 * queues, because those queues are role-guarded for Accountant/FM and the Owner
 * must see the same picture read-only.
 */
@Service
public class AiWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(AiWatchdogService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal LARGE_AMOUNT = new BigDecimal("50000");

    private final OverrideAuditService overrideAuditService;
    private final AuditEventRepository auditEventRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final AttachmentRepository attachmentRepo;
    private final BranchRepository branchRepo;
    private final DashboardService dashboardService;
    private final BranchScope branchScope;
    private final ObjectMapper objectMapper;

    public AiWatchdogService(OverrideAuditService overrideAuditService,
                             AuditEventRepository auditEventRepo,
                             ReceiveDocumentRepository receiveDocumentRepo,
                             ExpenseDocumentRepository expenseDocumentRepo,
                             SettlementLineRepository settlementLineRepo,
                             ExpenseLineRepository expenseLineRepo,
                             AttachmentRepository attachmentRepo,
                             BranchRepository branchRepo,
                             DashboardService dashboardService,
                             BranchScope branchScope,
                             ObjectMapper objectMapper) {
        this.overrideAuditService = overrideAuditService;
        this.auditEventRepo = auditEventRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.attachmentRepo = attachmentRepo;
        this.branchRepo = branchRepo;
        this.dashboardService = dashboardService;
        this.branchScope = branchScope;
        this.objectMapper = objectMapper;
    }

    /** Flag clusters worth an Owner's attention (FEAT-11). */
    @Transactional(readOnly = true)
    public List<AnomalyItem> anomalies(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        requireVisibleBranch(branchId);
        List<AnomalyItem> items = new ArrayList<>();
        items.addAll(overrideClusters(orgId, branchId));
        items.addAll(nearLimitExpenses(orgId, branchId));
        items.addAll(cashFlags(orgId, branchId));
        items.addAll(afterHoursSubmissions(orgId, branchId));
        log.info("AI anomalies built: orgId={} branchId={} flags={}", orgId, branchId, items.size());
        return items;
    }

    /** Pre-approval health per SUBMITTED document, riskiest first (FEAT-16). */
    @Transactional(readOnly = true)
    public List<RiskScore> riskScores(String queue, Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        requireVisibleBranch(branchId);
        // L2: "Expense" and "expense" are the same queue — never silently score receipts.
        String safeQueue = "expense".equalsIgnoreCase(queue) ? "expense" : "receipt";
        List<RiskScore> scores = "expense".equals(safeQueue)
            ? scoreExpenses(orgId, branchId)
            : scoreReceipts(orgId, branchId);
        scores.sort(Comparator.comparingInt(RiskScore::score).reversed());
        log.info("AI risk scored: orgId={} queue={} branchId={} docs={}",
            orgId, safeQueue, branchId, scores.size());
        return scores;
    }

    /** Why entries keep coming back queried, clustered by cause (FEAT-13). */
    @Transactional(readOnly = true)
    public List<QueryRoot> queryRoots(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        requireVisibleBranch(branchId);
        Instant to = Instant.now();
        Instant from = to.minus(90, ChronoUnit.DAYS);
        List<AuditEvent> queried = auditEventRepo.findForOverrideAudit(
            orgId, EventType.QUERIED, from, to, branchId, null);

        Map<String, Long> counts = new HashMap<>();
        for (AuditEvent event : queried) {
            String cause = classifyNote(readNote(event.getDetail()));
            counts.put(cause, counts.getOrDefault(cause, 0L) + 1);
        }
        List<QueryRoot> roots = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            roots.add(new QueryRoot(entry.getKey(), entry.getValue(), suggestionFor(entry.getKey())));
        }
        roots.sort(Comparator.comparingLong(QueryRoot::count).reversed());
        log.info("AI query roots built: orgId={} branchId={} events={} causes={}",
            orgId, branchId, queried.size(), roots.size());
        return roots;
    }

    // --- anomalies: one small rule per method ---

    private List<AnomalyItem> overrideClusters(Long orgId, Long branchId) {
        List<AnomalyItem> items = new ArrayList<>();
        Instant to = Instant.now();
        Instant from = to.minus(30, ChronoUnit.DAYS);
        List<OverrideAuditEntry> overrides = overrideAuditService.list(from, to, branchId, null);
        Map<String, List<OverrideAuditEntry>> byActor = new HashMap<>();
        for (OverrideAuditEntry entry : overrides) {
            byActor.computeIfAbsent(entry.actorName(), key -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<String, List<OverrideAuditEntry>> entry : byActor.entrySet()) {
            if (entry.getValue().size() >= 3) {
                OverrideAuditEntry sample = entry.getValue().get(0);
                items.add(new AnomalyItem("override-cluster", "urgent",
                    entry.getKey() + " overrode " + entry.getValue().size()
                        + " amounts in 30 days — review whether limits or training are the cause.",
                    sample.branchCode(), sample.documentNo()));
            }
        }
        return items;
    }

    private List<AnomalyItem> nearLimitExpenses(Long orgId, Long branchId) {
        List<AnomalyItem> items = new ArrayList<>();
        List<ExpenseDocument> docs = expenseDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, ExpenseWorkflowStatus.SUBMITTED);
        Map<Long, String> codes = branchCodes(orgId);
        Optional<Set<Long>> allowed = branchScope.allowedBranchIds();
        for (ExpenseDocument doc : docs) {
            if (!visible(allowed, branchId, doc.getBranchId()) || !doc.isOverLimit()) {
                continue;
            }
            items.add(new AnomalyItem("near-limit", "watch",
                "Expense " + label(doc.getDocumentNo(), doc.getId())
                    + " is over its sub-category limit — confirm the override reason before approving.",
                codes.get(doc.getBranchId()), doc.getDocumentNo()));
        }
        return items;
    }

    private List<AnomalyItem> cashFlags(Long orgId, Long branchId) {
        List<AnomalyItem> items = new ArrayList<>();
        // BranchComparisonRow already carries last-close variance + pending review per branch.
        for (BranchComparisonRow row : dashboardService.summary(branchId, "mtd").branchComparison()) {
            if (row.variance() != null && row.variance().signum() != 0) {
                items.add(new AnomalyItem("cash-variance", "urgent",
                    row.branchCode() + " closed with variance " + row.variance()
                        + " — ask for the count remark.",
                    row.branchCode(), null));
            }
            if (row.lastClosed() == null) {
                items.add(new AnomalyItem("unclosed-day", "watch",
                    row.branchCode() + " has no cash close on record — confirm the drawer is being closed daily.",
                    row.branchCode(), null));
            }
        }
        return items;
    }

    private List<AnomalyItem> afterHoursSubmissions(Long orgId, Long branchId) {
        List<AnomalyItem> items = new ArrayList<>();
        Map<Long, String> codes = branchCodes(orgId);
        Optional<Set<Long>> allowed = branchScope.allowedBranchIds();
        for (ReceiveDocument doc : receiveDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, WorkflowStatus.SUBMITTED)) {
            if (!visible(allowed, branchId, doc.getBranchId()) || !isAfterHours(doc.getSubmittedAt())) {
                continue;
            }
            items.add(new AnomalyItem("after-hours", "info",
                "Receipt " + label(doc.getDocumentNo(), doc.getId()) + " was submitted outside workshop hours.",
                codes.get(doc.getBranchId()), doc.getDocumentNo()));
        }
        for (ExpenseDocument doc : expenseDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, ExpenseWorkflowStatus.SUBMITTED)) {
            if (!visible(allowed, branchId, doc.getBranchId()) || !isAfterHours(doc.getSubmittedAt())) {
                continue;
            }
            items.add(new AnomalyItem("after-hours", "info",
                "Expense " + label(doc.getDocumentNo(), doc.getId()) + " was submitted outside workshop hours.",
                codes.get(doc.getBranchId()), doc.getDocumentNo()));
        }
        return items;
    }

    // --- risk scoring: additive signals, capped at 100 ---

    private List<RiskScore> scoreReceipts(Long orgId, Long branchId) {
        List<ReceiveDocument> docs = new ArrayList<>();
        Optional<Set<Long>> allowed = branchScope.allowedBranchIds();
        for (ReceiveDocument doc : receiveDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, WorkflowStatus.SUBMITTED)) {
            if (visible(allowed, branchId, doc.getBranchId())) {
                docs.add(doc);
            }
        }
        Map<Long, String> codes = branchCodes(orgId);
        List<Long> ids = docs.stream().map(ReceiveDocument::getId).toList();
        // M1: one line fetch feeds totals, override flags and line ids together.
        List<SettlementLine> lines = ids.isEmpty() ? List.of() : settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(orgId, ids);
        Map<Long, BigDecimal> totals = new HashMap<>();
        Map<Long, Boolean> overridden = new HashMap<>();
        Map<Long, Long> lineDoc = new HashMap<>();
        List<Long> lineIds = new ArrayList<>();
        for (SettlementLine line : lines) {
            totals.merge(line.getReceiveDocumentId(), line.getAmount(), BigDecimal::add);
            if (line.getOverriddenBy() != null) {
                overridden.put(line.getReceiveDocumentId(), true);
            }
            if (line.getId() != null) {
                lineDoc.put(line.getId(), line.getReceiveDocumentId());
                lineIds.add(line.getId());
            }
        }
        Map<Long, Long> docAttachments = attachmentCounts(orgId, ParentType.RECEIVE_DOCUMENT, ids);
        Set<Long> docsWithLineBills = docsWithAttachments(
            attachmentCounts(orgId, ParentType.SETTLEMENT_LINE, lineIds), lineDoc);
        List<RiskScore> scores = new ArrayList<>();
        for (ReceiveDocument doc : docs) {
            List<String> reasons = new ArrayList<>();
            int score = 0;
            score = addIf(score, reasons, Boolean.TRUE.equals(overridden.get(doc.getId())),
                30, "Amount overridden — check the reason");
            // M1: a bill on any line counts — only flag when neither doc nor lines have one.
            score = addIf(score, reasons,
                docAttachments.getOrDefault(doc.getId(), 0L) == 0
                    && !docsWithLineBills.contains(doc.getId()),
                15, "No bill attached");
            BigDecimal total = totals.getOrDefault(doc.getId(), BigDecimal.ZERO);
            score = addIf(score, reasons, total.compareTo(LARGE_AMOUNT) >= 0,
                15, "Large amount " + total + " — verify the supporting bill");
            score = addIf(score, reasons, isAfterHours(doc.getSubmittedAt()),
                10, "Submitted outside workshop hours");
            scores.add(new RiskScore("receipt", doc.getId(), doc.getDocumentNo(),
                codes.get(doc.getBranchId()), Math.min(score, 100), reasons));
        }
        return scores;
    }

    private List<RiskScore> scoreExpenses(Long orgId, Long branchId) {
        List<ExpenseDocument> docs = new ArrayList<>();
        Optional<Set<Long>> allowed = branchScope.allowedBranchIds();
        for (ExpenseDocument doc : expenseDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, ExpenseWorkflowStatus.SUBMITTED)) {
            if (visible(allowed, branchId, doc.getBranchId())) {
                docs.add(doc);
            }
        }
        Map<Long, String> codes = branchCodes(orgId);
        List<Long> ids = docs.stream().map(ExpenseDocument::getId).toList();
        List<ExpenseLine> lines = ids.isEmpty() ? List.of() : expenseLineRepo
            .findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(orgId, ids);
        Map<Long, BigDecimal> totals = new HashMap<>();
        Map<Long, Boolean> overridden = new HashMap<>();
        Map<Long, Long> lineDoc = new HashMap<>();
        List<Long> lineIds = new ArrayList<>();
        for (ExpenseLine line : lines) {
            totals.merge(line.getExpenseDocumentId(), line.getAmount(), BigDecimal::add);
            if (line.getOverriddenBy() != null) {
                overridden.put(line.getExpenseDocumentId(), true);
            }
            if (line.getId() != null) {
                lineDoc.put(line.getId(), line.getExpenseDocumentId());
                lineIds.add(line.getId());
            }
        }
        Map<Long, Long> docAttachments = attachmentCounts(orgId, ParentType.EXPENSE_DOCUMENT, ids);
        Set<Long> docsWithLineBills = docsWithAttachments(
            attachmentCounts(orgId, ParentType.EXPENSE_LINE, lineIds), lineDoc);
        List<RiskScore> scores = new ArrayList<>();
        for (ExpenseDocument doc : docs) {
            List<String> reasons = new ArrayList<>();
            int score = 0;
            score = addIf(score, reasons, Boolean.TRUE.equals(overridden.get(doc.getId())),
                30, "Amount overridden — check the reason");
            score = addIf(score, reasons, doc.isOverLimit(),
                25, "Over the sub-category limit");
            score = addIf(score, reasons,
                docAttachments.getOrDefault(doc.getId(), 0L) == 0
                    && !docsWithLineBills.contains(doc.getId()),
                15, "No bill attached");
            BigDecimal total = totals.getOrDefault(doc.getId(), BigDecimal.ZERO);
            score = addIf(score, reasons, total.compareTo(LARGE_AMOUNT) >= 0,
                15, "Large amount " + total + " — verify the supporting bill");
            score = addIf(score, reasons, isAfterHours(doc.getSubmittedAt()),
                10, "Submitted outside workshop hours");
            scores.add(new RiskScore("expense", doc.getId(), doc.getDocumentNo(),
                codes.get(doc.getBranchId()), Math.min(score, 100), reasons));
        }
        return scores;
    }

    private int addIf(int score, List<String> reasons, boolean signal, int points, String reason) {
        if (signal) {
            reasons.add(reason);
            return score + points;
        }
        return score;
    }

    // --- batched helpers: one query per signal, never per document ---

    /** Docs holding at least one line-level bill, resolved via line id → doc id. */
    private Set<Long> docsWithAttachments(Map<Long, Long> lineCounts, Map<Long, Long> lineDoc) {
        Set<Long> docs = new HashSet<>();
        for (Map.Entry<Long, Long> entry : lineCounts.entrySet()) {
            Long docId = lineDoc.get(entry.getKey());
            if (docId != null) {
                docs.add(docId);
            }
        }
        return docs;
    }

    private Map<Long, Long> attachmentCounts(Long orgId, ParentType type, List<Long> parentIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (parentIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : attachmentRepo.countByParentIdIn(orgId, type, parentIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    // --- query-note clustering ---

    private String readNote(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(detailJson);
            JsonNode note = node.get("note");
            return note != null && note.isTextual() ? note.asText("") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String classifyNote(String note) {
        String lower = note.toLowerCase();
        // Specific complaints first: "amount mismatch with bill total" mentions a
        // bill but is really an amount problem — generic bill keywords go last.
        if (lower.contains("amount") || lower.contains("mismatch") || lower.contains("total")
            || lower.contains("difference")) {
            return "Amount does not match the bill";
        }
        if (lower.contains("attach") || lower.contains("bill") || lower.contains("photo")
            || lower.contains("receipt") || lower.contains("blur")) {
            return "Missing or unreadable bill attachment";
        }
        if (lower.contains("dbm") || lower.contains("invoice") || lower.contains("job card")) {
            return "Missing DBM ID / invoice reference";
        }
        if (lower.contains("categor") || lower.contains("wrong") || lower.contains("mode")
            || lower.contains("bank") || lower.contains("upi") || lower.contains("ref")) {
            return "Wrong category / payment mode / reference";
        }
        if (note.isBlank()) {
            return "Queried without a note";
        }
        return "Other: " + (note.length() > 60 ? note.substring(0, 60) + "…" : note);
    }

    private String suggestionFor(String cause) {
        return switch (cause) {
            case "Missing or unreadable bill attachment" ->
                "Retrain cashiers on attaching a clear bill photo before submit; link the Help article.";
            case "Missing DBM ID / invoice reference" ->
                "Make DBM/invoice capture part of the counter checklist for repeat customers.";
            case "Amount does not match the bill" ->
                "Ask cashiers to re-add the bill total before submitting large lines.";
            case "Wrong category / payment mode / reference" ->
                "Review Masters dropdowns for confusing near-duplicate names.";
            case "Queried without a note" ->
                "Ask reviewers to always leave a note so the cashier can fix it first time.";
            default -> "Read the query notes with the reviewer and agree one fix.";
        };
    }

    // --- shared guards ---

    private void requireVisibleBranch(Long branchId) {
        if (branchId != null && !branchScope.canSeeBranch(branchId)) {
            throw DamsException.forbidden("Branch " + branchId + " is outside your access");
        }
    }

    /**
     * M2: the allowed set is resolved once per call and passed in — never once
     * per document, since every resolution hits the user/organisation tables.
     */
    private boolean visible(Optional<Set<Long>> allowed, Long filter, Long docBranchId) {
        if (filter != null && !filter.equals(docBranchId)) {
            return false;
        }
        return allowed.map(set -> set.contains(docBranchId)).orElse(true);
    }

    private boolean isAfterHours(Instant submittedAt) {
        if (submittedAt == null) {
            return false;
        }
        int hour = submittedAt.atZone(IST).getHour();
        // Workshop hours run roughly 07:00–20:00 IST; anything outside deserves a glance.
        return hour < 7 || hour >= 20;
    }

    private Map<Long, String> branchCodes(Long orgId) {
        Map<Long, String> codes = new HashMap<>();
        for (Branch branch : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
            codes.put(branch.getId(), branch.getCode());
        }
        return codes;
    }

    private String label(String documentNo, Long id) {
        return documentNo != null ? documentNo : "#" + id;
    }
}
