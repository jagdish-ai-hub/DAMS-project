package com.dams.dashboard;

import com.dams.audit.repository.AuditEventRepository;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.cash.service.DrawerService;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.dashboard.dto.DashboardSummary;
import com.dams.dashboard.service.ClaimAdjustmentService;
import com.dams.dashboard.service.DashboardService;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.repository.VehicleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dashboard aggregate rules (Stage 9): money KPIs come straight from the APPROVED-only
 * repo sums; cash-in-hand is the summed drawer position (never part of collections /
 * expenses); the period drives the date window.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final long ORG = 1L;

    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private CashDocumentRepository cashDocumentRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;
    @Mock private ExpenseModeRepository expenseModeRepo;
    @Mock private ClaimTypeRepository claimTypeRepo;
    @Mock private ReceiverRepository receiverRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private CashDayCloseRepository cashDayCloseRepo;
    @Mock private DrawerService drawerService;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private AuditEventRepository auditEventRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private ClaimAdjustmentService claimAdjustments;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(settlementLineRepo, expenseLineRepo, receiveDocumentRepo, expenseDocumentRepo,
            cashDocumentRepo, settlementModeRepo, expenseCategoryRepo, expenseModeRepo, claimTypeRepo, receiverRepo, branchRepo,
            cashDayCloseRepo, drawerService, jobCardRepo, customerRepo, vehicleRepo, claimCloseRepo,
            pendingAmountCalculator, auditEventRepo, userRepo, new ObjectMapper(), claimAdjustments);
        TenantContext.setOrgId(ORG);

        lenient().when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(branch(3L, "OOR"), branch(2L, "OOB")));
        lenient().when(settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of());
        lenient().when(expenseCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of());
        lenient().when(settlementLineRepo.dashboardCollectionsByMode(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(expenseLineRepo.dashboardExpensesByCategory(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(settlementLineRepo.dashboardCollectionsByBranch(any(), any(), any())).thenReturn(List.of());
        lenient().when(expenseLineRepo.dashboardExpensesByBranch(any(), any(), any())).thenReturn(List.of());
        lenient().when(settlementLineRepo.dashboardCollectionsByDay(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(expenseLineRepo.dashboardExpensesByDay(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(any(), any()))
            .thenReturn(java.util.Optional.empty());
        lenient().when(cashDayCloseRepo.findByOrgIdOrderByCloseDateDesc(ORG)).thenReturn(List.of());
        // Batched drawer roll-up: each branch's computed position (was one position() call per branch).
        lenient().when(drawerService.runningPositions(eq(ORG), any(), any()))
            .thenReturn(java.util.Map.of(2L, running("4000"), 3L, running("4000")));
        lenient().when(claimAdjustments.between(eq(ORG), any(), any())).thenReturn(List.of());
        lenient().when(receiveDocumentRepo.findDraftOnlyJobCardIds(ORG)).thenReturn(List.of());
        lenient().when(receiveDocumentRepo.countPendingReviewByBranch(ORG)).thenReturn(List.of());
        lenient().when(expenseDocumentRepo.countPendingReviewByBranch(ORG)).thenReturn(List.of());
        lenient().when(cashDocumentRepo.countPendingReviewByBranch(ORG)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void summary_kpis_comeFromApprovedSumsAndExcludeCashFromCollectionsAndExpenses() {
        when(settlementLineRepo.dashboardCollections(eq(ORG), any(), any(), isNull())).thenReturn(new BigDecimal("120000"));
        when(expenseLineRepo.dashboardExpenses(eq(ORG), any(), any(), isNull())).thenReturn(new BigDecimal("30000"));
        when(receiveDocumentRepo.countPendingReviewByBranch(ORG)).thenReturn(List.of(new Object[][]{{2L, 2L}}));
        when(expenseDocumentRepo.countPendingReviewByBranch(ORG)).thenReturn(List.of(new Object[][]{{2L, 1L}}));

        DashboardSummary s = service.summary(null, "mtd");

        assertThat(s.scope()).isEqualTo("ALL");
        assertThat(s.kpis().collections()).isEqualByComparingTo("120000");
        assertThat(s.kpis().expenses()).isEqualByComparingTo("30000");
        assertThat(s.kpis().net()).isEqualByComparingTo("90000");
        // two branches, each drawer 4000 → 8000, and no cash_document ever touched collections/expenses
        assertThat(s.kpis().cashInHand()).isEqualByComparingTo("8000");
        assertThat(s.kpis().pendingReview()).isEqualTo(3L);
    }

    /** rev 71: the approved-only headline is accompanied by what is still awaiting approval. */
    @Test
    void summary_kpis_carryTheAwaitingApprovalAmountsBesideTheApprovedOnes() {
        when(settlementLineRepo.dashboardCollections(eq(ORG), any(), any(), isNull())).thenReturn(new BigDecimal("95376"));
        when(expenseLineRepo.dashboardExpenses(eq(ORG), any(), any(), isNull())).thenReturn(BigDecimal.ZERO);
        when(settlementLineRepo.dashboardCollectionsAwaiting(eq(ORG), any(), any(), isNull())).thenReturn(new BigDecimal("925813"));
        when(expenseLineRepo.dashboardExpensesAwaiting(eq(ORG), any(), any(), isNull())).thenReturn(new BigDecimal("8570"));

        DashboardSummary s = service.summary(null, "mtd");

        assertThat(s.kpis().collections()).isEqualByComparingTo("95376");
        assertThat(s.kpis().collectionsAwaiting()).isEqualByComparingTo("925813");
        assertThat(s.kpis().expensesAwaiting()).isEqualByComparingTo("8570");
        // the awaiting money never leaks into the approved-only headline or Net
        assertThat(s.kpis().net()).isEqualByComparingTo("95376");
    }

    /** rev 71: cash in hand is the RUNNING position — days that were never closed are not skipped. */
    @Test
    void summary_cashInHand_isTheRunningPosition() {
        when(drawerService.runningPositions(eq(ORG), any(), any()))
            .thenReturn(java.util.Map.of(2L, running("99250"), 3L, running("83180")));

        DashboardSummary s = service.summary(null, "mtd");

        assertThat(s.kpis().cashInHand()).isEqualByComparingTo("182430");
        assertThat(s.branchComparison()).extracting(r -> r.cashInHand().intValue())
            .containsExactlyInAnyOrder(99250, 83180);
    }

    private static ClaimAdjustmentService.Adjustment adjustment(Long branchId, LocalDate date, String diff) {
        BigDecimal d = new BigDecimal(diff);
        return new ClaimAdjustmentService.Adjustment(95L, branchId, 21L, date, date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            new BigDecimal("5200"), new BigDecimal("5200").subtract(d), d, 500L, "OOR-SEP26-R-036");
    }

    /** rev 73: a closed claim counts at the FM's final amount — the difference rides on its close day. */
    @Test
    void summary_closedClaimAdjustment_movesCollections_trend_branchTable_andModeSplit() {
        LocalDate today = com.dams.common.time.OrgTime.today();
        when(settlementLineRepo.dashboardCollections(eq(ORG), any(), any(), isNull())).thenReturn(new BigDecimal("10000"));
        when(claimAdjustments.between(eq(ORG), any(), any())).thenReturn(List.of(
            adjustment(3L, today, "4200"),                 // closed higher than the lines
            adjustment(3L, today.minusDays(1), "-1500"),   // closed lower
            adjustment(2L, today, "-200")));

        DashboardSummary s = service.summary(null, "mtd");

        assertThat(s.kpis().collections()).isEqualByComparingTo("12500");     // 10000 + 4200 − 1500 − 200
        assertThat(s.kpis().net()).isEqualByComparingTo("12500");
        assertThat(s.trend().get(s.trend().size() - 1).collections()).isEqualByComparingTo("4000");   // 4200 − 200 today
        assertThat(s.byMode()).anyMatch(m -> m.name().equals(DashboardService.CLAIM_ADJUSTMENT_LABEL)
            && m.amount().compareTo(new BigDecimal("2500")) == 0);
        assertThat(s.branchComparison()).filteredOn(r -> r.branchId() == 3L)
            .allSatisfy(r -> assertThat(r.collections()).isEqualByComparingTo("2700"));
        // it is not cash — the drawer figure is whatever DrawerService says, untouched
        assertThat(s.kpis().cashInHand()).isEqualByComparingTo("8000");
    }

    @Test
    void summary_closedClaimAdjustment_respectsTheBranchFilter_andIsAbsentWhenZero() {
        LocalDate today = com.dams.common.time.OrgTime.today();
        when(settlementLineRepo.dashboardCollections(eq(ORG), any(), any(), eq(3L))).thenReturn(new BigDecimal("1000"));
        when(branchRepo.findByIdAndOrgId(3L, ORG)).thenReturn(java.util.Optional.of(branch(3L, "OOR")));
        when(claimAdjustments.between(eq(ORG), any(), any())).thenReturn(List.of(
            adjustment(3L, today, "300"), adjustment(2L, today, "9999")));

        DashboardSummary s = service.summary(3L, "mtd");

        assertThat(s.kpis().collections()).isEqualByComparingTo("1300");       // OOB's claim is not counted

        when(claimAdjustments.between(eq(ORG), any(), any())).thenReturn(List.of());
        assertThat(service.summary(3L, "mtd").byMode()).noneMatch(m -> m.name().equals(DashboardService.CLAIM_ADJUSTMENT_LABEL));
    }

    private com.dams.jobcard.entity.JobCard jobCard(long id, String invoice) {
        com.dams.jobcard.entity.JobCard jc = new com.dams.jobcard.entity.JobCard();
        ReflectionTestUtils.setField(jc, "id", id);
        jc.setOrgId(ORG);
        jc.setBranchId(3L);
        jc.setCustomerId(21L);
        jc.setInvoiceAmount(new BigDecimal(invoice));
        return jc;
    }

    /** rev 73: a blank draft is not a receivable; a rejected one still is; so is a draft that already has money on it. */
    @Test
    void outstanding_leavesOutBlankDrafts_butKeepsEverythingElseOwed() {
        when(jobCardRepo.findByOrgId(ORG)).thenReturn(List.of(
            jobCard(28L, "5200"),     // only a blank draft  -> dropped
            jobCard(26L, "33000"),    // receipt was rejected -> stays
            jobCard(30L, "8000"),     // draft that already carries payment lines -> stays
            jobCard(31L, "4000")));   // an ordinary part-paid job -> stays
        when(receiveDocumentRepo.findDraftOnlyJobCardIds(ORG)).thenReturn(List.of(28L, 30L));
        when(settlementLineRepo.sumAmountByJobCard(ORG)).thenReturn(List.of(
            new Object[]{30L, new BigDecimal("1000")}, new Object[]{31L, new BigDecimal("1500")}));

        var items = service.outstanding(null);

        assertThat(items).extracting(i -> i.amount().intValue()).containsExactlyInAnyOrder(33000, 7000, 2500);
        assertThat(items).extracting(com.dams.dashboard.dto.OutstandingItem::amount)
            .doesNotContain(new BigDecimal("5200"));
    }

    @Test
    void summary_period_today_windowsTheDateRangeToOneDay() {
        when(settlementLineRepo.dashboardCollections(eq(ORG), any(), any(), isNull())).thenReturn(BigDecimal.ZERO);
        when(expenseLineRepo.dashboardExpenses(eq(ORG), any(), any(), isNull())).thenReturn(BigDecimal.ZERO);

        service.summary(null, "today");

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(settlementLineRepo).dashboardCollections(eq(ORG), from.capture(), to.capture(), isNull());
        assertThat(from.getValue()).isEqualTo(to.getValue()); // today → single day
    }

    @Test
    void summary_trend_hasFourteenContiguousDays() {
        when(settlementLineRepo.dashboardCollections(eq(ORG), any(), any(), isNull())).thenReturn(BigDecimal.ZERO);
        when(expenseLineRepo.dashboardExpenses(eq(ORG), any(), any(), isNull())).thenReturn(BigDecimal.ZERO);

        DashboardSummary s = service.summary(null, "mtd");

        assertThat(s.trend()).hasSize(14);
        assertThat(s.trend().get(13).date()).isEqualTo(s.trend().get(0).date().plusDays(13));
    }

    private static Branch branch(long id, String code) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setCode(code);
        b.setName(code + " branch");
        return b;
    }

    private static DrawerService.RunningPosition running(String position) {
        BigDecimal p = new BigDecimal(position);
        return new DrawerService.RunningPosition(null, BigDecimal.ZERO, false, null, p, p);
    }
}
