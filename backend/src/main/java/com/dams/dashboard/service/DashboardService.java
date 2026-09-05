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
import com.dams.dashboard.dto.NamedAmount;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.dto.TrendPoint;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
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
    private static final List<EventType> ACTIVITY_TYPES = List.of(
        EventType.SUBMITTED, EventType.VERIFIED, EventType.APPROVED,
        EventType.QUERIED, EventType.REJECTED, EventType.CLOSED, EventType.SETTLED, EventType.CREATED);

    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;
    private final ReceiveCategoryRepository receiveCategoryRepo;
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

    public DashboardService(SettlementLineRepository settlementLineRepo,
                            ExpenseLineRepository expenseLineRepo,
                            ReceiveDocumentRepository receiveDocumentRepo,
                            ExpenseDocumentRepository expenseDocumentRepo,
                            CashDocumentRepository cashDocumentRepo,
                            SettlementModeRepository settlementModeRepo,
                            ExpenseCategoryRepository expenseCategoryRepo,
                            ReceiveCategoryRepository receiveCategoryRepo,
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
                            ObjectMapper objectMapper) {
        this.settlementLineRepo = settlementLineRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
        this.receiveCategoryRepo = receiveCategoryRepo;
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
        Map<Long, BigDecimal> positions = drawerService.computedPositions(orgId, branchIds, today);
        Map<Long, Long> pendingByBranch = mergeCounts(
            receiveDocumentRepo.countPendingReviewByBranch(orgId),
            expenseDocumentRepo.countPendingReviewByBranch(orgId),
            cashDocumentRepo.countPendingReviewByBranch(orgId));
        Map<Long, ClaimAndVariance> lastCloseByBranch = latestCloseByBranch(orgId);

        BigDecimal collections = nz(settlementLineRepo.dashboardCollections(orgId, from, today, branchId));
        BigDecimal expenses = nz(expenseLineRepo.dashboardExpenses(orgId, from, today, branchId));
        BigDecimal cashInHand = scoped != null
            ? positions.getOrDefault(scoped.getId(), BigDecimal.ZERO)
            : positions.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingReview = scoped != null
            ? pendingByBranch.getOrDefault(scoped.getId(), 0L)
            : pendingByBranch.values().stream().mapToLong(Long::longValue).sum();

        DashboardKpis kpis = new DashboardKpis(collections, expenses,
            collections.subtract(expenses), cashInHand, pendingReview);

        return new DashboardSummary(
            scoped != null ? scoped.getCode() : "ALL",
            "today".equals(period) ? "today" : "mtd",
            kpis,
            trend(orgId, branchId, trendFrom, today),
            named(settlementLineRepo.dashboardCollectionsByMode(orgId, from, today, branchId), settlementModeNames(orgId)),
            named(expenseLineRepo.dashboardExpensesByCategory(orgId, from, today, branchId), expenseCategoryNames(orgId)),
            branchComparison(orgId, from, today, branchId, branches, positions, pendingByBranch, lastCloseByBranch));
    }

    private List<TrendPoint> trend(Long orgId, Long branchId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> col = dayMap(settlementLineRepo.dashboardCollectionsByDay(orgId, from, to, branchId));
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
                                                       Map<Long, ClaimAndVariance> lastCloseByBranch) {
        Map<Long, BigDecimal> colByBranch = idAmountMap(settlementLineRepo.dashboardCollectionsByBranch(orgId, from, to));
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
        Map<Long, BigDecimal> receivedByJc = idAmountMap(settlementLineRepo.sumAmountByJobCard(orgId));
        Map<Long, String> branchCodes = branchCodeMap(orgId);
        Map<Long, ReceiveCategory> categoriesById = receiveCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)
            .stream().collect(Collectors.toMap(ReceiveCategory::getId, c -> c, (a, b) -> a));

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
            ReceiveCategory cat = categoriesById.get(jc.getCategoryId());
            if (cat != null && cat.isClaim()) {
                continue;
            }
            BigDecimal pending = pendingFor(jc, receivedByJc);
            if (pending.signum() <= 0) {
                continue;
            }
            Customer c = customersById.get(jc.getCustomerId());
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
            if (jc == null) {
                continue;
            }
            ReceiveCategory cat = categoriesById.get(jc.getCategoryId());
            if (cat == null || !cat.isClaim()) {
                continue;
            }
            BigDecimal invoice = jc.getInvoiceAmount() != null ? jc.getInvoiceAmount() : BigDecimal.ZERO;
            BigDecimal received = receivedByJc.getOrDefault(jc.getId(), BigDecimal.ZERO);
            BigDecimal owed = invoice.subtract(received).max(BigDecimal.ZERO);
            Customer c = customersById.get(jc.getCustomerId());
            String code = branchCodes.getOrDefault(d.getBranchId(), "?");
            out.add(new OutstandingItem("claim",
                c != null ? c.getName() : "Warranty / AMC claim",
                code + " · " + cat.getName() + " · awaiting Eicher settlement",
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
            default -> type.name();
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
