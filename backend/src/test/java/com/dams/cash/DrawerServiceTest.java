package com.dams.cash;

import com.dams.cash.entity.BranchCashOpening;
import com.dams.cash.entity.CashDayClose;
import com.dams.cash.entity.CashDirection;
import com.dams.cash.repository.BranchCashOpeningRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.cash.service.DrawerService;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.SettlementLineRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The drawer formula: opening + cash-mode receipts + cash IN − cash-mode expenses − cash OUT,
 * and the opening chain (previous close → branch opening → zero + not-set).
 */
@ExtendWith(MockitoExtension.class)
class DrawerServiceTest {

    private static final long ORG = 1L;
    private static final long BRANCH = 3L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 30);

    @Mock private BranchCashOpeningRepository branchCashOpeningRepo;
    @Mock private CashDayCloseRepository cashDayCloseRepo;
    @Mock private CashDocumentRepository cashDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private ExpenseModeRepository expenseModeRepo;

    private DrawerService service;

    @BeforeEach
    void setUp() {
        service = new DrawerService(branchCashOpeningRepo, cashDayCloseRepo, cashDocumentRepo,
            settlementLineRepo, expenseLineRepo, settlementModeRepo, expenseModeRepo);

        lenient().when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(settlementMode(10L)));
        lenient().when(expenseModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of(expenseMode(20L)));
        lenient().when(settlementLineRepo.sumCashModeForBranchDate(eq(ORG), eq(BRANCH), eq(DATE), anyList()))
            .thenReturn(new BigDecimal("5000"));
        lenient().when(expenseLineRepo.sumCashModeForBranchDate(eq(ORG), eq(BRANCH), eq(DATE), anyList()))
            .thenReturn(new BigDecimal("800"));
        lenient().when(cashDocumentRepo.sumForBranchDateAndDirection(ORG, BRANCH, DATE, CashDirection.IN))
            .thenReturn(new BigDecimal("20000"));
        lenient().when(cashDocumentRepo.sumForBranchDateAndDirection(ORG, BRANCH, DATE, CashDirection.OUT))
            .thenReturn(new BigDecimal("15000"));
    }

    @Test
    void opening_isThePreviousDaysCountedClose_andPositionAddsUp() {
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(ORG, BRANCH, DATE))
            .thenReturn(Optional.of(close(new BigDecimal("30000"))));

        DrawerService.DrawerPosition p = service.position(ORG, BRANCH, DATE);

        assertThat(p.opening()).isEqualByComparingTo("30000");
        assertThat(p.openingSet()).isTrue();
        // 30000 + 5000 + 20000 − 800 − 15000
        assertThat(p.computedPosition()).isEqualByComparingTo("39200");
    }

    @Test
    void opening_fallsBackToTheBranchOpening_whenThereIsNoCloseYet() {
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(ORG, BRANCH, DATE))
            .thenReturn(Optional.empty());
        when(branchCashOpeningRepo.findByOrgIdAndBranchId(ORG, BRANCH))
            .thenReturn(Optional.of(branchOpening(new BigDecimal("10000"), LocalDate.of(2026, 7, 1))));

        DrawerService.DrawerPosition p = service.position(ORG, BRANCH, DATE);

        assertThat(p.opening()).isEqualByComparingTo("10000");
        assertThat(p.openingSet()).isTrue();
    }

    @Test
    void opening_isZeroAndFlaggedNotSet_whenNeitherACloseNorABranchOpeningExists() {
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(ORG, BRANCH, DATE))
            .thenReturn(Optional.empty());
        when(branchCashOpeningRepo.findByOrgIdAndBranchId(ORG, BRANCH)).thenReturn(Optional.empty());

        DrawerService.DrawerPosition p = service.position(ORG, BRANCH, DATE);

        assertThat(p.opening()).isEqualByComparingTo("0");
        assertThat(p.openingSet()).isFalse();
    }

    @Test
    void noCashModesConfigured_meansTheReceiptsAndExpensesTermsAreZero_withoutQueryingLines() {
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(ORG, BRANCH, DATE))
            .thenReturn(Optional.empty());
        when(branchCashOpeningRepo.findByOrgIdAndBranchId(ORG, BRANCH)).thenReturn(Optional.empty());
        when(settlementModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of());
        when(expenseModeRepo.findByOrgIdAndCashTrue(ORG)).thenReturn(List.of());

        DrawerService.DrawerPosition p = service.position(ORG, BRANCH, DATE);

        assertThat(p.cashReceipts()).isEqualByComparingTo("0");
        assertThat(p.cashExpenses()).isEqualByComparingTo("0");
        // 0 + 0 + 20000 − 0 − 15000
        assertThat(p.computedPosition()).isEqualByComparingTo("5000");
        verify(settlementLineRepo, never()).sumCashModeForBranchDate(any(), any(), any(), any());
        verify(expenseLineRepo, never()).sumCashModeForBranchDate(any(), any(), any(), any());
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

    private static CashDayClose close(BigDecimal counted) {
        CashDayClose c = new CashDayClose();
        c.setOrgId(ORG);
        c.setBranchId(BRANCH);
        c.setCloseDate(DATE.minusDays(1));
        c.setCountedAmount(counted);
        return c;
    }

    private static BranchCashOpening branchOpening(BigDecimal amount, LocalDate on) {
        BranchCashOpening o = new BranchCashOpening();
        o.setOrgId(ORG);
        o.setBranchId(BRANCH);
        o.setOpeningDate(on);
        o.setAmount(amount);
        return o;
    }
}
