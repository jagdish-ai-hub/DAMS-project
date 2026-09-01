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
import com.dams.dashboard.service.DashboardService;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
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
    @Mock private ReceiveCategoryRepository receiveCategoryRepo;
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

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(settlementLineRepo, expenseLineRepo, receiveDocumentRepo, expenseDocumentRepo,
            cashDocumentRepo, settlementModeRepo, expenseCategoryRepo, receiveCategoryRepo, branchRepo,
            cashDayCloseRepo, drawerService, jobCardRepo, customerRepo, vehicleRepo, claimCloseRepo,
            pendingAmountCalculator, auditEventRepo, userRepo, new ObjectMapper());
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
        lenient().when(drawerService.computedPositions(eq(ORG), any(), any()))
            .thenReturn(java.util.Map.of(2L, new BigDecimal("4000"), 3L, new BigDecimal("4000")));
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
}
