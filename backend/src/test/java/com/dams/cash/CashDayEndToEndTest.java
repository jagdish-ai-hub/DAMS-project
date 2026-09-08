package com.dams.cash;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.dto.CashDayCloseResponse;
import com.dams.cash.dto.CloseDayRequest;
import com.dams.cash.dto.CreateReopenRequest;
import com.dams.cash.dto.ReopenRequestResponse;
import com.dams.cash.entity.BranchCashOpening;
import com.dams.cash.entity.CashCloseReopenRequest;
import com.dams.cash.entity.CashDayClose;
import com.dams.cash.entity.CashDirection;
import com.dams.cash.entity.ReopenRequestStatus;
import com.dams.cash.repository.BranchCashOpeningRepository;
import com.dams.cash.repository.CashCloseReopenRequestRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.cash.service.CashCloseService;
import com.dams.cash.service.CashDateLock;
import com.dams.cash.service.CashDocumentService;
import com.dams.cash.service.CashPostingGuard;
import com.dams.cash.service.CashReopenService;
import com.dams.cash.service.DrawerService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One full cash day through the <em>real</em> services, sharing the same mocked repositories:
 * drawer math → end-of-day close → lock → reopen request → FM approve → re-close.
 *
 * <p>This is the branch's daily money story from AGENT.md decision #1, proved as a chain
 * rather than isolated units: the computed figure the cashier counts against is the same
 * figure the close locks, and the only way back in is the audited reopen flow — there is
 * deliberately no direct reopen endpoint.
 */
@ExtendWith(MockitoExtension.class)
class CashDayEndToEndTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long FM_ID = 8L;
    private static final long BRANCH = 3L;
    private static final LocalDate DAY = LocalDate.of(2026, 7, 30);

    // Opening 30000 + cash receipts 5000 + Cash IN 20000 − cash expenses 800 − Cash OUT 15000.
    private static final BigDecimal COMPUTED = new BigDecimal("39200");

    @Mock private BranchCashOpeningRepository branchCashOpeningRepo;
    @Mock private CashDayCloseRepository cashDayCloseRepo;
    @Mock private CashDocumentRepository cashDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private ExpenseModeRepository expenseModeRepo;
    @Mock private CashDocumentService cashDocumentService;
    @Mock private BranchRepository branchRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private BranchScope branchScope;
    @Mock private CashPostingGuard postingGuard;
    @Mock private CashCloseReopenRequestRepository reopenRepo;
    @Mock private AuditService auditService;
    @Mock private com.dams.organization.repository.OrganizationRepository orgRepo;

    private DrawerService drawerService;
    private CashCloseService closeService;
    private CashReopenService reopenService;
    private CashDateLock dateLock;

    @BeforeEach
    void setUp() {
        drawerService = new DrawerService(branchCashOpeningRepo, cashDayCloseRepo, cashDocumentRepo,
            settlementLineRepo, expenseLineRepo, settlementModeRepo, expenseModeRepo);
        closeService = new CashCloseService(cashDayCloseRepo, branchCashOpeningRepo, cashDocumentRepo,
            drawerService, cashDocumentService, branchRepo, userRepo, postingGuard, auditService, orgRepo);
        reopenService = new CashReopenService(reopenRepo, cashDayCloseRepo, branchRepo, userRepo,
            branchScope, postingGuard, auditService);
        dateLock = new CashDateLock(cashDayCloseRepo, branchRepo);
        TenantContext.setOrgId(ORG);

        // The day's takings and movements, as the Cash page would show them.
        lenient().when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(settlementMode(10L)));
        lenient().when(expenseModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(expenseMode(20L)));
        lenient().when(settlementLineRepo.sumCashModeForBranchDate(eq(ORG), eq(BRANCH), eq(DAY), any()))
            .thenReturn(new BigDecimal("5000"));
        lenient().when(expenseLineRepo.sumCashModeForBranchDate(eq(ORG), eq(BRANCH), eq(DAY), any()))
            .thenReturn(new BigDecimal("800"));
        lenient().when(cashDocumentRepo.sumForBranchDateAndDirection(ORG, BRANCH, DAY, CashDirection.IN))
            .thenReturn(new BigDecimal("20000"));
        lenient().when(cashDocumentRepo.sumForBranchDateAndDirection(ORG, BRANCH, DAY, CashDirection.OUT))
            .thenReturn(new BigDecimal("15000"));

        // Yesterday closed at 30000 counted — that is today's opening.
        lenient().when(cashDayCloseRepo
                .findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(ORG, BRANCH, DAY))
            .thenReturn(Optional.of(previousClose()));
        lenient().when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH))
            .thenReturn(Optional.empty());

        lenient().when(postingGuard.requireCashier(ORG)).thenReturn(cashier());
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(userRepo.findById(CASHIER_ID)).thenReturn(Optional.of(cashier()));
        lenient().when(userRepo.findByIdAndOrganization_Id(FM_ID, ORG))
            .thenReturn(Optional.of(financeManager()));
        lenient().when(branchScope.currentUserId()).thenReturn(FM_ID);
        lenient().when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        lenient().when(cashDayCloseRepo.save(any(CashDayClose.class))).thenAnswer(inv -> {
            CashDayClose c = inv.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", 500L);
            return c;
        });
        lenient().when(reopenRepo.save(any(CashCloseReopenRequest.class))).thenAnswer(inv -> {
            CashCloseReopenRequest r = inv.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", 200L);
            return r;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void drawerBreakdown_matchesTheCashPageFormula_forEveryTerm() {
        DrawerService.DrawerPosition p = drawerService.position(ORG, BRANCH, DAY);

        assertThat(p.opening()).isEqualByComparingTo("30000");
        assertThat(p.openingSet()).isTrue();
        assertThat(p.cashReceipts()).isEqualByComparingTo("5000");
        assertThat(p.cashIn()).isEqualByComparingTo("20000");
        assertThat(p.cashExpenses()).isEqualByComparingTo("800");
        assertThat(p.cashOut()).isEqualByComparingTo("15000");
        // Opening + cash-mode receipts + Cash In − cash-mode expenses − Cash Out.
        assertThat(p.computedPosition()).isEqualByComparingTo(COMPUTED);
    }

    @Test
    void fullDayLifecycle_closeLocksTheDay_reopenRequestFmApproveThenReclose() {
        // 1. The cashier counts exactly the computed figure — no remark needed.
        CashDayCloseResponse closed = closeService.closeDay(closeRequest(COMPUTED, null));
        assertThat(closed.computedClosing()).isEqualByComparingTo(COMPUTED);
        assertThat(closed.variance()).isEqualByComparingTo("0");
        CashDayClose savedClose = closeOf(closed);

        // 2. The closed day now refuses fresh cash movement (the lock reads the latest
        // close — a movement dated on or before it is rejected).
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH))
            .thenReturn(Optional.of(savedClose));
        assertThatThrownBy(() -> dateLock.requireCashDateOpen(ORG, BRANCH, DAY))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("closed through");

        // 3. The only way back in: a reopen request with a mandatory reason.
        when(cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(ORG, BRANCH, DAY))
            .thenReturn(Optional.of(savedClose));
        when(reopenRepo.existsByOrgIdAndBranchIdAndCloseDateAndStatus(
            ORG, BRANCH, DAY, ReopenRequestStatus.PENDING)).thenReturn(false);
        ReopenRequestResponse requested = reopenService.request(reopenRequest());
        assertThat(requested.status()).isEqualTo(ReopenRequestStatus.PENDING);
        assertThat(requested.closeDate()).isEqualTo(DAY);

        // 4. The FM approves — the close row is removed so the day can be re-closed.
        when(reopenRepo.findByIdAndOrgId(200L, ORG)).thenReturn(Optional.of(pendingRequest()));
        ReopenRequestResponse approved = reopenService.approve(200L);
        assertThat(approved.status()).isEqualTo(ReopenRequestStatus.APPROVED);
        verify(cashDayCloseRepo).delete(savedClose);

        // 5. With the lock row deleted the day closes again against the same computed figure.
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH))
            .thenReturn(Optional.empty());
        CashDayCloseResponse reclosed = closeService.closeDay(closeRequest(COMPUTED, null));
        assertThat(reclosed.computedClosing()).isEqualByComparingTo(COMPUTED);
    }

    @Test
    void varianceGate_shortCountWithoutRemarkIsRejected_withRemarkItRecords() {
        // 1000 short of the computed 39200.
        assertThatThrownBy(() -> closeService.closeDay(closeRequest(new BigDecimal("38200"), "  ")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("variance remark is required");
        verify(cashDayCloseRepo, never()).save(any());

        CashDayCloseResponse r = closeService.closeDay(
            closeRequest(new BigDecimal("38200"), "Counted short — recount witnessed by FM"));
        assertThat(r.variance()).isEqualByComparingTo("-1000");
        assertThat(r.varianceRemark()).isEqualTo("Counted short — recount witnessed by FM");
        verify(auditService).recordUserEvent(eq("CashDayClose"), eq(500L), eq(EventType.CLOSED),
            eq(CASHIER_ID), any());
    }

    @Test
    void reopenMakerChecker_fmCannotApproveTheirOwnRequest() {
        CashCloseReopenRequest ownRequest = pendingRequest();
        ownRequest.setRequestedBy(FM_ID);
        when(reopenRepo.findByIdAndOrgId(200L, ORG)).thenReturn(Optional.of(ownRequest));

        assertThatThrownBy(() -> reopenService.approve(200L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("you requested it");
        verify(cashDayCloseRepo, never()).delete(any());
    }

    @Test
    void firstEverOpening_comesFromTheAccountantSetOpening_thenRollsForward() {
        LocalDate firstDay = LocalDate.of(2026, 7, 1);
        lenient().when(cashDayCloseRepo
                .findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(ORG, BRANCH, firstDay))
            .thenReturn(Optional.empty());
        when(branchCashOpeningRepo.findByOrgIdAndBranchId(ORG, BRANCH))
            .thenReturn(Optional.of(branchOpening(new BigDecimal("10000"), firstDay)));

        DrawerService.Opening opening = drawerService.openingFor(ORG, BRANCH, firstDay);

        assertThat(opening.amount()).isEqualByComparingTo("10000");
        assertThat(opening.set()).isTrue();
    }

    // ---------------------------------------------------------- fixtures

    private static CloseDayRequest closeRequest(BigDecimal counted, String remark) {
        CloseDayRequest r = new CloseDayRequest();
        r.setCloseDate(DAY);
        r.setCountedAmount(counted);
        r.setVarianceRemark(remark);
        return r;
    }

    private static CreateReopenRequest reopenRequest() {
        CreateReopenRequest r = new CreateReopenRequest();
        r.setCloseDate(DAY);
        r.setReason("Found a missed cash receipt after closing");
        return r;
    }

    /** Rebuild the persisted close from the close response the service returned. */
    private static CashDayClose closeOf(CashDayCloseResponse r) {
        CashDayClose c = new CashDayClose();
        ReflectionTestUtils.setField(c, "id", 500L);
        c.setOrgId(ORG);
        c.setBranchId(BRANCH);
        c.setCloseDate(DAY);
        c.setOpeningAmount(new BigDecimal("30000"));
        c.setComputedClosing(r.computedClosing());
        c.setCountedAmount(r.computedClosing());
        c.setVariance(r.variance());
        c.setClosedBy(CASHIER_ID);
        return c;
    }

    private static CashDayClose previousClose() {
        CashDayClose c = new CashDayClose();
        c.setOrgId(ORG);
        c.setBranchId(BRANCH);
        c.setCloseDate(DAY.minusDays(1));
        c.setCountedAmount(new BigDecimal("30000"));
        return c;
    }

    private static CashCloseReopenRequest pendingRequest() {
        CashCloseReopenRequest r = new CashCloseReopenRequest();
        ReflectionTestUtils.setField(r, "id", 200L);
        r.setOrgId(ORG);
        r.setBranchId(BRANCH);
        r.setCloseDate(DAY);
        r.setReason("Found a missed cash receipt after closing");
        r.setStatus(ReopenRequestStatus.PENDING);
        r.setRequestedBy(CASHIER_ID);
        return r;
    }

    private static BranchCashOpening branchOpening(BigDecimal amount, LocalDate on) {
        BranchCashOpening o = new BranchCashOpening();
        o.setOrgId(ORG);
        o.setBranchId(BRANCH);
        o.setOpeningDate(on);
        o.setAmount(amount);
        return o;
    }

    private static SettlementMode settlementMode(long id) {
        SettlementMode m = new SettlementMode();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCash(true);
        return m;
    }

    private static ExpenseMode expenseMode(long id) {
        ExpenseMode m = new ExpenseMode();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCash(true);
        return m;
    }

    private static AppUser cashier() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(BRANCH);
        return u;
    }

    private static AppUser financeManager() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", FM_ID);
        u.setRole(Role.FINANCE_MANAGER);
        return u;
    }

    private static Branch branch() {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", BRANCH);
        b.setOrgId(ORG);
        b.setCode("OOR");
        b.setName("Rayagada");
        b.setActive(true);
        return b;
    }
}
