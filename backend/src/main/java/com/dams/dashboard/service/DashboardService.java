package com.dams.dashboard.service;

import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.cash.service.DrawerService;
import com.dams.common.exception.DamsException;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.dashboard.dto.ActivityItem;
import com.dams.dashboard.dto.BranchComparisonRow;
import com.dams.dashboard.dto.DashboardKpis;
import com.dams.dashboard.dto.DashboardSummary;
import com.dams.dashboard.dto.MoneyMovementItem;
import com.dams.dashboard.dto.NamedAmount;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.dto.TrendPoint;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.entity.ClaimType;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owner dashboard aggregates (Stage 9). Read-only. Two rules throughout:
 *   - money figures ({@code collections}, {@code expenses}) count APPROVED documents only —
 *     the dashboard shows verified, Tally-grade numbers, not work-in-progress;
 *   - cash In/Out (`cash_document`) is never part of collections or expenses (AGENT.md
 *     decision #1) — it only feeds {@code cashInHand} via {@link DrawerService}.
 */
@Service
public class DashboardService {

    private static final int TREND_DAYS = 14;
    /**
     * Everything an Owner would want to see happen to money — including (rev 71) payments added,
     * overrides, status / category / claim-type changes, transfers to claim, approval requests,
     * pre-approvals and cash re-opens. Role switches and customer-attach bookkeeping are left out.
     */
    private static final List<EventType> ACTIVITY_TYPES = List.of(
        EventType.SUBMITTED, EventType.VERIFIED, EventType.APPROVED,
        EventType.QUERIED, EventType.REJECTED, EventType.CLOSED, EventType.SETTLED, EventType.CREATED,
        EventType.LINE_ADDED, EventType.OVERRIDE, EventType.STATUS_CHANGED, EventType.CATEGORY_CHANGED,
        EventType.CLAIM_TYPE_CHANGED, EventType.TRANSFERRED_TO_CLAIM, EventType.APPROVAL_REQUESTED,
        EventType.PRE_APPROVED, EventType.CASH_REOPENED);

    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;
    private final ExpenseModeRepository expenseModeRepo;
    private final ClaimTypeRepository claimTypeRepo;
    private final ReceiverRepository receiverRepo;
    private final BranchRepository branchRepo;
    private final CashDayCloseRepository cashDayCloseRepo;
    private final DrawerService drawerService;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final AuditEventRepository auditEventRepo;
    private final AppUserRepository userRepo;
    private final ObjectMapper objectMapper;
    private final ClaimAdjustmentService claimAdjustments;

    public DashboardService(SettlementLineRepository settlementLineRepo,
                            ExpenseLineRepository expenseLineRepo,
                            ReceiveDocumentRepository receiveDocumentRepo,
                            ExpenseDocumentRepository expenseDocumentRepo,
                            CashDocumentRepository cashDocumentRepo,
                            SettlementModeRepository settlementModeRepo,
                            ExpenseCategoryRepository expenseCategoryRepo,
                            ExpenseModeRepository expenseModeRepo,
                            ClaimTypeRepository claimTypeRepo,
                            ReceiverRepository receiverRepo,
                            BranchRepository branchRepo,
                            CashDayCloseRepository cashDayCloseRepo,
                            DrawerService drawerService,
                            JobCardRepository jobCardRepo,
                            CustomerRepository customerRepo,
                            VehicleRepository vehicleRepo,
                            ClaimCloseRepository claimCloseRepo,
                            PendingAmountCalculator pendingAmountCalculator,
                            AuditEventRepository auditEventRepo,
                            AppUserRepository userRepo,
                            ObjectMapper objectMapper,
                            ClaimAdjustmentService claimAdjustments) {
        this.settlementLineRepo = settlementLineRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
        this.expenseModeRepo = expenseModeRepo;
        this.claimTypeRepo = claimTypeRepo;
        this.receiverRepo = receiverRepo;
        this.branchRepo = branchRepo;
        this.cashDayCloseRepo = cashDayCloseRepo;
        this.drawerService = drawerService;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.auditEventRepo = auditEventRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
        this.claimAdjustments = claimAdjustments;
    }

    // ==================================================================== summary

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long branchId, String period) {
        Long orgId = TenantContext.requireOrgId();
        Branch scoped = resolveBranch(orgId, branchId);
        LocalDate today = OrgTime.today();
        LocalDate from = "today".equals(period) ? today : today.withDayOfMonth(1);
        LocalDate trendFrom = today.minusDays(TREND_DAYS - 1L);

        // Shared batch loads — computed once here, threaded through the per-branch table below,
        // so the dashboard is a fixed handful of queries rather than ~8 per branch.
        List<Branch> branches = branchRepo.findByOrgIdOrderByCodeAsc(orgId);
        List<Long> branchIds = branches.stream().map(Branch::getId).toList();
        Map<Long, BigDecimal> positions = new HashMap<>();
        drawerService.runningPositions(orgId, branchIds, today).forEach((id, p) -> positions.put(id, p.position()));
        Map<Long, Long> pendingByBranch = mergeCounts(
            receiveDocumentRepo.countPendingReviewByBranch(orgId),
            expenseDocumentRepo.countPendingReviewByBranch(orgId),
            cashDocumentRepo.countPendingReviewByBranch(orgId));
        Map<Long, ClaimAndVariance> lastCloseByBranch = latestCloseByBranch(orgId);

        // rev 73: a closed claim counts at the Finance Manager's final amount — one labelled adjustment per
        // claim on its close day. One fetch covers both the period and the 14-day trend window.
        List<ClaimAdjustmentService.Adjustment> adjustments =
            claimAdjustments.between(orgId, from.isBefore(trendFrom) ? from : trendFrom, today);
        BigDecimal collections = nz(settlementLineRepo.dashboardCollections(orgId, from, today, branchId))
            .add(adjustmentSum(adjustments, from, today, branchId));
        BigDecimal expenses = nz(expenseLineRepo.dashboardExpenses(orgId, from, today, branchId));
        BigDecimal cashInHand = scoped != null
            ? positions.getOrDefault(scoped.getId(), BigDecimal.ZERO)
            : positions.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingReview = scoped != null
            ? pendingByBranch.getOrDefault(scoped.getId(), 0L)
            : pendingByBranch.values().stream().mapToLong(Long::longValue).sum();

        BigDecimal collectionsAwaiting = nz(settlementLineRepo.dashboardCollectionsAwaiting(orgId, from, today, branchId));
        BigDecimal expensesAwaiting = nz(expenseLineRepo.dashboardExpensesAwaiting(orgId, from, today, branchId));

        DashboardKpis kpis = new DashboardKpis(collections, expenses,
            collections.subtract(expenses), cashInHand, pendingReview, collectionsAwaiting, expensesAwaiting);

        return new DashboardSummary(
            scoped != null ? scoped.getCode() : "ALL",
            "today".equals(period) ? "today" : "mtd",
            kpis,
            trend(orgId, branchId, trendFrom, today, adjustments),
            withAdjustment(named(settlementLineRepo.dashboardCollectionsByMode(orgId, from, today, branchId), settlementModeNames(orgId)),
                adjustmentSum(adjustments, from, today, branchId)),
            named(expenseLineRepo.dashboardExpensesByCategory(orgId, from, today, branchId), expenseCategoryNames(orgId)),
            branchComparison(orgId, from, today, branchId, branches, positions, pendingByBranch, lastCloseByBranch,
                adjustments));
    }

    private List<TrendPoint> trend(Long orgId, Long branchId, LocalDate from, LocalDate to,
                                   List<ClaimAdjustmentService.Adjustment> adjustments) {
        Map<LocalDate, BigDecimal> col = dayMap(settlementLineRepo.dashboardCollectionsByDay(orgId, from, to, branchId));
        for (ClaimAdjustmentService.Adjustment a : adjustments) {
            if (inWindow(a, from, to, branchId)) {
                col.merge(a.date(), a.amount(), BigDecimal::add);
            }
        }
        Map<LocalDate, BigDecimal> exp = dayMap(expenseLineRepo.dashboardExpensesByDay(orgId, from, to, branchId));
        List<TrendPoint> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.add(new TrendPoint(d, col.getOrDefault(d, BigDecimal.ZERO), exp.getOrDefault(d, BigDecimal.ZERO)));
        }
        return out;
    }

    private List<BranchComparisonRow> branchComparison(Long orgId, LocalDate from, LocalDate to, Long branchId,
                                                       List<Branch> branches,
                                                       Map<Long, BigDecimal> positions,
                                                       Map<Long, Long> pendingByBranch,
                                                       Map<Long, ClaimAndVariance> lastCloseByBranch,
                                                       List<ClaimAdjustmentService.Adjustment> adjustments) {
        Map<Long, BigDecimal> colByBranch = idAmountMap(settlementLineRepo.dashboardCollectionsByBranch(orgId, from, to));
        for (ClaimAdjustmentService.Adjustment a : adjustments) {
            if (inWindow(a, from, to, null)) {
                colByBranch.merge(a.branchId(), a.amount(), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> expByBranch = idAmountMap(expenseLineRepo.dashboardExpensesByBranch(orgId, from, to));

        List<BranchComparisonRow> rows = new ArrayList<>();
        for (Branch b : branches) {
            if (branchId != null && !branchId.equals(b.getId())) {
                continue;
            }
            BigDecimal col = colByBranch.getOrDefault(b.getId(), BigDecimal.ZERO);
            BigDecimal exp = expByBranch.getOrDefault(b.getId(), BigDecimal.ZERO);
            ClaimAndVariance cv = lastCloseByBranch.getOrDefault(b.getId(), NO_CLOSE);
            rows.add(new BranchComparisonRow(b.getId(), b.getCode(), b.getName(),
                col, exp, col.subtract(exp),
                positions.getOrDefault(b.getId(), BigDecimal.ZERO),
                cv.date(), cv.variance(),
                pendingByBranch.getOrDefault(b.getId(), 0L)));
        }
        return rows;
    }

    private static boolean inWindow(ClaimAdjustmentService.Adjustment a, LocalDate from, LocalDate to, Long branchId) {
        return !a.date().isBefore(from) && !a.date().isAfter(to)
            && (branchId == null || branchId.equals(a.branchId()));
    }

    private static BigDecimal adjustmentSum(List<ClaimAdjustmentService.Adjustment> adjustments,
                                            LocalDate from, LocalDate to, Long branchId) {
        return adjustments.stream().filter(a -> inWindow(a, from, to, branchId))
            .map(ClaimAdjustmentService.Adjustment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The "Collections by mode" split plus, when non-zero, the closed-claim adjustment as its own named slice. */
    private static List<NamedAmount> withAdjustment(List<NamedAmount> byMode, BigDecimal adjustment) {
        if (adjustment.signum() == 0) {
            return byMode;
        }
        List<NamedAmount> out = new ArrayList<>(byMode);
        out.add(new NamedAmount(CLAIM_ADJUSTMENT_LABEL, adjustment));
        return out;
    }

    /** Label of the closed-claim adjustment — in the mode split and on the drill-down rows. */
    public static final String CLAIM_ADJUSTMENT_LABEL = "Claim final amount adjustment";

    private record ClaimAndVariance(LocalDate date, BigDecimal variance) {}

    private static final ClaimAndVariance NO_CLOSE = new ClaimAndVariance(null, null);

    /** Each branch's most recent close (date + variance), from one org-wide query. */
    private Map<Long, ClaimAndVariance> latestCloseByBranch(Long orgId) {
        Map<Long, ClaimAndVariance> m = new HashMap<>();
        for (var c : cashDayCloseRepo.findByOrgIdOrderByCloseDateDesc(orgId)) {
            m.putIfAbsent(c.getBranchId(), new ClaimAndVariance(c.getCloseDate(), c.getVariance()));
        }
        return m;
    }

    /** Sum three {@code [branchId, count]} result sets into one {@code branchId -> total} map. */
    @SafeVarargs
    private static Map<Long, Long> mergeCounts(List<Object[]>... resultSets) {
        Map<Long, Long> m = new HashMap<>();
        for (List<Object[]> rows : resultSets) {
            for (Object[] r : rows) {
                m.merge(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), Long::sum);
            }
        }
        return m;
    }

    // ==================================================================== reconciliation breakdown

    /**
     * The receipt lines behind the Collections KPI for a period/branch — same APPROVED-only
     * filter {@link com.dams.receive.repository.SettlementLineRepository#dashboardCollections}
     * uses, so this always sums to exactly what the card shows.
     */
    @Transactional(readOnly = true)
    public List<MoneyMovementItem> collectionsBreakdown(Long branchId, String period) {
        Long orgId = TenantContext.requireOrgId();
        resolveBranch(orgId, branchId);
        LocalDate today = OrgTime.today();
        LocalDate from = "today".equals(period) ? today : today.withDayOfMonth(1);

        List<Object[]> rows = settlementLineRepo.findApprovedForBreakdown(orgId, from, today, branchId);
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, String> modeNames = settlementModeNames(orgId);
        Map<Long, String> customerNames = customerNamesFor(orgId, rows.stream()
            .map(r -> ((com.dams.jobcard.entity.JobCard) r[2]).getCustomerId()).toList());

        List<MoneyMovementItem> out = new ArrayList<>();
        for (Object[] r : rows) {
            SettlementLine l = (SettlementLine) r[0];
            ReceiveDocument d = (ReceiveDocument) r[1];
            com.dams.jobcard.entity.JobCard jc = (com.dams.jobcard.entity.JobCard) r[2];
            String branchCode = branchCodes.getOrDefault(d.getBranchId(), "?");
            out.add(new MoneyMovementItem("receipt", d.getId(), d.getDocumentNo(), d.getWorkflowStatus().name(),
                l.getTransactionDate(), l.getCreatedAt(), branchCode,
                jc.getCustomerId() == null ? "—" : customerNames.getOrDefault(jc.getCustomerId(), "—"),
                branchCode + "-JC-" + jc.getId(),
                modeNames.getOrDefault(l.getSettlementModeId(), "—"),
                l.getAmount()));
        }

        // rev 73: the closed-claim adjustments — one signed, clickable row per claim, so the rows
        // add up to the card. They carry the claim's branch and customer.
        List<ClaimAdjustmentService.Adjustment> adjustments = claimAdjustments.between(orgId, from, today).stream()
            .filter(a -> branchId == null || branchId.equals(a.branchId())).toList();
        Map<Long, String> adjCustomers = customerNamesFor(orgId, adjustments.stream().map(ClaimAdjustmentService.Adjustment::customerId)
            .filter(java.util.Objects::nonNull).toList());
        for (ClaimAdjustmentService.Adjustment a : adjustments) {
            out.add(new MoneyMovementItem("claim-adjustment", a.documentId(), a.documentNo(), "APPROVED",
                a.date(), a.closedAt(), branchCodes.getOrDefault(a.branchId(), "?"),
                a.customerId() == null ? "—" : adjCustomers.getOrDefault(a.customerId(), "—"),
                a.describe(), CLAIM_ADJUSTMENT_LABEL, a.amount()));
        }
        out.sort(Comparator.comparing(MoneyMovementItem::date).reversed());
        return out;
    }

    /**
     * The expense lines behind the Expenses KPI for a period/branch — same APPROVED-or-CLOSED
     * filter {@link com.dams.expense.repository.ExpenseLineRepository#dashboardExpenses} uses.
     */
    @Transactional(readOnly = true)
    public List<MoneyMovementItem> expensesBreakdown(Long branchId, String period) {
        Long orgId = TenantContext.requireOrgId();
        resolveBranch(orgId, branchId);
        LocalDate today = OrgTime.today();
        LocalDate from = "today".equals(period) ? today : today.withDayOfMonth(1);

        List<Object[]> rows = expenseLineRepo.findApprovedForBreakdown(orgId, from, today, branchId);
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, String> modeNames = expenseModeNames(orgId);
        Map<Long, String> categoryNames = expenseCategoryNames(orgId);
        Map<Long, String> receiverNames = receiverNamesFor(orgId, rows.stream()
            .map(r -> ((ExpenseDocument) r[1]).getReceiverId()).toList());

        List<MoneyMovementItem> out = new ArrayList<>();
        for (Object[] r : rows) {
            ExpenseLine l = (ExpenseLine) r[0];
            ExpenseDocument d = (ExpenseDocument) r[1];
            String branchCode = branchCodes.getOrDefault(d.getBranchId(), "?");
            out.add(new MoneyMovementItem("expense", d.getId(), d.getDocumentNo(), d.getWorkflowStatus().name(),
                l.getTransactionDate(), l.getCreatedAt(), branchCode,
                receiverNames.getOrDefault(d.getReceiverId(), "—"),
                categoryNames.getOrDefault(d.getExpenseCategoryId(), "—"),
                modeNames.getOrDefault(l.getExpenseModeId(), "—"),
                l.getAmount()));
        }
        return out;
    }

    private Map<Long, String> customerNamesFor(Long orgId, List<Long> customerIds) {
        List<Long> distinct = customerIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, distinct).stream()
            .collect(Collectors.toMap(com.dams.customer.entity.Customer::getId, com.dams.customer.entity.Customer::getName));
    }

    private Map<Long, String> receiverNamesFor(Long orgId, List<Long> receiverIds) {
        List<Long> distinct = receiverIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return receiverRepo.findByOrgIdAndIdIn(orgId, distinct).stream()
            .collect(Collectors.toMap(Receiver::getId, Receiver::getName));
    }

    private Map<Long, String> expenseModeNames(Long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (var mode : expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            m.put(mode.getId(), mode.getName());
        }
        return m;
    }

    // ==================================================================== cash breakdown

    /**
     * The movements behind the Cash in hand KPI (rev 71): per branch, the opening / last counted
     * amount and every cash movement since, expenses and Cash Out as negative amounts — so the
     * rows sum to exactly the card. Same running rule as {@link DrawerService#runningPositions}.
     */
    @Transactional(readOnly = true)
    public List<MoneyMovementItem> cashBreakdown(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        resolveBranch(orgId, branchId);
        LocalDate today = OrgTime.today();
        List<MoneyMovementItem> out = new ArrayList<>();
        for (Branch b : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
            if (branchId != null && !branchId.equals(b.getId())) {
                continue;
            }
            out.addAll(drawerService.runningBreakdown(orgId, b.getId(), b.getCode(), today));
        }
        return out;
    }

    // ==================================================================== outstanding

    @Transactional(readOnly = true)
    public List<OutstandingItem> outstanding(Long branchId) {
        Long orgId = TenantContext.requireOrgId();
        resolveBranch(orgId, branchId);
        List<OutstandingItem> out = new ArrayList<>();

        // Batch loads — the whole method is now a fixed set of queries, not one-per-job-card.
        List<JobCard> jobCards = jobCardRepo.findByOrgId(orgId);
        Map<Long, JobCard> jobCardsById = jobCards.stream()
            .collect(Collectors.toMap(JobCard::getId, j -> j, (a, b) -> a));
        java.util.Set<Long> closedJcIds = new java.util.HashSet<>(claimCloseRepo.findJobCardIdsByOrgId(orgId));
        // rev 73: a job card whose only receipts are blank drafts (nothing submitted, no payment lines)
        // is not a receivable yet.
        java.util.Set<Long> draftOnlyJcIds = new java.util.HashSet<>(receiveDocumentRepo.findDraftOnlyJobCardIds(orgId));
        Map<Long, BigDecimal> receivedByJc = idAmountMap(settlementLineRepo.sumAmountByJobCard(orgId));
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, ClaimType> claimTypesById = claimTypeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)
            .stream().collect(Collectors.toMap(ClaimType::getId, c -> c, (a, b) -> a));

        List<Long> customerIds = jobCards.stream().map(JobCard::getCustomerId).distinct().toList();
        Map<Long, Customer> customersById = customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, customerIds)
            .stream().collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
        List<Long> vehicleIds = jobCards.stream().map(JobCard::getVehicleId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Vehicle> vehiclesById = vehicleIds.isEmpty() ? Map.of()
            : vehicleRepo.findByOrgIdAndIdIn(orgId, vehicleIds)
                .stream().collect(Collectors.toMap(Vehicle::getId, v -> v, (a, b) -> a));

        for (JobCard jc : jobCards) {
            if (branchId != null && !branchId.equals(jc.getBranchId())) {
                continue;
            }
            if (closedJcIds.contains(jc.getId())) {
                continue;
            }
            if (jc.getClaimTypeId() != null) {
                continue;
            }
            if (draftOnlyJcIds.contains(jc.getId()) && receivedByJc.getOrDefault(jc.getId(), BigDecimal.ZERO).signum() == 0) {
                continue;
            }
            BigDecimal pending = pendingFor(jc, receivedByJc);
            if (pending.signum() <= 0) {
                continue;
            }
            Customer c = jc.getCustomerId() == null ? null : customersById.get(jc.getCustomerId());
            Vehicle v = jc.getVehicleId() == null ? null : vehiclesById.get(jc.getVehicleId());
            String code = branchCodes.getOrDefault(jc.getBranchId(), "?");
            out.add(new OutstandingItem(
                jc.isB2b() ? "b2b" : "job-card",
                c != null ? c.getName() : "—",
                (v != null ? v.getVehicleNo() + " · " : "") + code + " · " + code + "-JC-" + jc.getId(),
                pending, code + "-JC-" + jc.getId(), code));
        }

        // Open claims — approved claim receipts with no ClaimClose yet.
        java.util.Set<Long> seenClaimJcs = new java.util.HashSet<>();
        for (var d : receiveDocumentRepo.findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
                orgId, com.dams.receive.entity.WorkflowStatus.APPROVED)) {
            if (branchId != null && !branchId.equals(d.getBranchId())) {
                continue;
            }
            if (closedJcIds.contains(d.getJobCardId()) || !seenClaimJcs.add(d.getJobCardId())) {
                continue;
            }
            JobCard jc = jobCardsById.get(d.getJobCardId());
            if (jc == null || jc.getClaimTypeId() == null) {
                continue;
            }
            ClaimType claimType = claimTypesById.get(jc.getClaimTypeId());
            BigDecimal invoice = jc.getInvoiceAmount() != null ? jc.getInvoiceAmount() : BigDecimal.ZERO;
            BigDecimal received = receivedByJc.getOrDefault(jc.getId(), BigDecimal.ZERO);
            BigDecimal owed = invoice.subtract(received).max(BigDecimal.ZERO);
            Customer c = jc.getCustomerId() == null ? null : customersById.get(jc.getCustomerId());
            String code = branchCodes.getOrDefault(d.getBranchId(), "?");
            out.add(new OutstandingItem("claim",
                c != null ? c.getName() : "Warranty / AMC claim",
                code + " · " + (claimType != null ? claimType.getName() : "Claim") + " · awaiting Eicher settlement",
                owed.signum() > 0 ? owed : invoice, d.getDocumentNo(), code));
        }
        out.sort(Comparator.comparing(OutstandingItem::amount).reversed());
        return out;
    }

    /** Pending-amount maths without a DB hit — mirrors {@link PendingAmountCalculator}. */
    private static BigDecimal pendingFor(JobCard jc, Map<Long, BigDecimal> receivedByJc) {
        if (jc.getInvoiceAmount() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal received = receivedByJc.getOrDefault(jc.getId(), BigDecimal.ZERO);
        BigDecimal pending = jc.getInvoiceAmount().subtract(received);
        return pending.signum() < 0 ? BigDecimal.ZERO : pending;
    }

    // ==================================================================== activity

    @Transactional(readOnly = true)
    public List<ActivityItem> activity(Long branchId, int limit) {
        Long orgId = TenantContext.requireOrgId();
        resolveBranch(orgId, branchId);
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, String> userNames = new HashMap<>();

        List<ActivityItem> out = new ArrayList<>();
        for (AuditEvent e : auditEventRepo.findRecentActivity(orgId, branchId, ACTIVITY_TYPES,
                PageRequest.of(0, Math.max(1, Math.min(limit, 50))))) {
            String docNo = documentNoFor(orgId, e.getEntityType(), e.getEntityId());
            out.add(new ActivityItem(
                actorName(e.getActorId(), userNames),
                humanAction(e.getEventType(), e.getDetail()),
                docNo,
                describeEntity(e.getEntityType()),
                null,
                e.getBranchId() == null ? null : branchCodes.get(e.getBranchId()),
                e.getCreatedAt()));
        }
        return out;
    }

    // ==================================================================== helpers

    private Branch resolveBranch(Long orgId, Long branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepo.findByIdAndOrgId(branchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", branchId));
    }

    private Map<Long, String> settlementModeNames(Long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (SettlementMode s : settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            m.put(s.getId(), s.getName());
        }
        return m;
    }

    private Map<Long, String> expenseCategoryNames(Long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (ExpenseCategory c : expenseCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            m.put(c.getId(), c.getName());
        }
        return m;
    }

    private Map<Long, String> branchCodeMap(Long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (Branch b : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
            m.put(b.getId(), b.getCode());
        }
        return m;
    }

    private static List<NamedAmount> named(List<Object[]> rows, Map<Long, String> names) {
        List<NamedAmount> out = new ArrayList<>();
        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            out.add(new NamedAmount(names.getOrDefault(id, "—"), (BigDecimal) r[1]));
        }
        out.sort(Comparator.comparing(NamedAmount::amount).reversed());
        return out;
    }

    private static Map<Long, BigDecimal> idAmountMap(List<Object[]> rows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }
        return m;
    }

    private static Map<LocalDate, BigDecimal> dayMap(List<Object[]> rows) {
        Map<LocalDate, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((LocalDate) r[0], (BigDecimal) r[1]);
        }
        return m;
    }

    private String actorName(Long actorId, Map<Long, String> cache) {
        if (actorId == null) {
            return "System";
        }
        return cache.computeIfAbsent(actorId, id -> userRepo.findById(id).map(AppUser::getName).orElse("User #" + id));
    }

    private String documentNoFor(Long orgId, String entityType, Long entityId) {
        return switch (entityType) {
            case "ReceiveDocument" -> receiveDocumentRepo.findByIdAndOrgId(entityId, orgId).map(d -> d.getDocumentNo()).orElse(null);
            case "ExpenseDocument" -> expenseDocumentRepo.findByIdAndOrgId(entityId, orgId).map(d -> d.getDocumentNo()).orElse(null);
            case "CashDocument" -> cashDocumentRepo.findByIdAndOrgId(entityId, orgId).map(d -> d.getDocumentNo()).orElse(null);
            case "JobCard" -> jobCardRepo.findByIdAndOrgId(entityId, orgId)
                .map(jc -> branchCodeMap(orgId).getOrDefault(jc.getBranchId(), "?") + "-JC-" + jc.getId()).orElse(null);
            default -> null;
        };
    }

    private static String describeEntity(String entityType) {
        return switch (entityType) {
            case "ReceiveDocument" -> "receipt";
            case "ExpenseDocument" -> "expense";
            case "CashDocument" -> "cash movement";
            case "JobCard" -> "job card";
            case "CashDayClose" -> "cash close";
            default -> entityType;
        };
    }

    private String humanAction(EventType type, String detailJson) {
        if (type == EventType.SUBMITTED && detailJson != null) {
            try {
                Map<?, ?> d = objectMapper.readValue(detailJson, Map.class);
                if (Boolean.TRUE.equals(d.get("resubmit"))) {
                    return "Resubmitted";
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return switch (type) {
            case CREATED -> "Created";
            case SUBMITTED -> "Submitted";
            case VERIFIED -> "Verified";
            case APPROVED -> "Approved";
            case QUERIED -> "Queried";
            case REJECTED -> "Rejected";
            case CLOSED -> "Closed";
            case SETTLED -> "Settled";
            case LINE_ADDED -> "Added a payment to";
            case OVERRIDE -> "Overrode an amount on";
            case STATUS_CHANGED -> "Changed the status of";
            case CATEGORY_CHANGED -> "Changed the category of";
            case CLAIM_TYPE_CHANGED -> "Changed the claim type of";
            case TRANSFERRED_TO_CLAIM -> "Moved to claim:";
            case APPROVAL_REQUESTED -> "Asked for approval on";
            case PRE_APPROVED -> "Pre-approved";
            case CASH_REOPENED -> "Re-opened the";
            default -> type.name();
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
