package com.dams.cash.service;

import com.dams.cash.entity.CashDirection;
import com.dams.cash.repository.BranchCashOpeningRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dams.cash.entity.BranchCashOpening;
import com.dams.cash.entity.CashDayClose;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one implementation of the Cash-page drawer formula (AGENT.md decision #1):
 *
 *   position = opening
 *            + cash-mode receipts        (settlement lines whose mode has is_cash)
 *            + cash IN                   (cash_document, direction IN)
 *            − cash-mode expenses        (expense lines whose mode has is_cash)
 *            − cash OUT                  (cash_document, direction OUT)
 *
 * for one branch on one date (Asia/Kolkata day boundary — the caller passes the date).
 *
 * <b>What counts:</b> every contributing document that is not DRAFT and not REJECTED. The
 * money is physically in the drawer the moment it changes hands — review state (SUBMITTED /
 * VERIFIED / QUERIED / APPROVED, or CLOSED for an expense) doesn't change that. REJECTED is
 * the one exclusion: a rejected movement or line is taken to mean the cash was returned or
 * never received, so it never entered the drawer.
 *
 * <b>opening:</b> the most recent {@code cash_day_close.counted_amount} strictly before the
 * date; if the branch has never closed, its one-time {@code branch_cash_opening.amount}
 * (when its {@code opening_date} is on or before the date); otherwise zero, with
 * {@code openingSet = false} so the UI can prompt the Accountant to set it.
 */
@Service
public class DrawerService {

    private final BranchCashOpeningRepository branchCashOpeningRepo;
    private final CashDayCloseRepository cashDayCloseRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final ExpenseModeRepository expenseModeRepo;

    public DrawerService(BranchCashOpeningRepository branchCashOpeningRepo,
                         CashDayCloseRepository cashDayCloseRepo,
                         CashDocumentRepository cashDocumentRepo,
                         SettlementLineRepository settlementLineRepo,
                         ExpenseLineRepository expenseLineRepo,
                         SettlementModeRepository settlementModeRepo,
                         ExpenseModeRepository expenseModeRepo) {
        this.branchCashOpeningRepo = branchCashOpeningRepo;
        this.cashDayCloseRepo = cashDayCloseRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.expenseModeRepo = expenseModeRepo;
    }

    /** The full breakdown behind the drawer position for one branch/date. */
    public record DrawerPosition(
        BigDecimal opening,
        boolean openingSet,
        BigDecimal cashReceipts,
        BigDecimal cashIn,
        BigDecimal cashExpenses,
        BigDecimal cashOut,
        BigDecimal computedPosition) {
    }

    @Transactional(readOnly = true)
    public DrawerPosition position(Long orgId, Long branchId, LocalDate date) {
        Opening opening = openingFor(orgId, branchId, date);

        List<Long> cashSettlementModeIds = settlementModeRepo.findByOrgIdAndCashTrue(orgId)
            .stream().map(SettlementMode::getId).toList();
        List<Long> cashExpenseModeIds = expenseModeRepo.findByOrgIdAndCashTrue(orgId)
            .stream().map(ExpenseMode::getId).toList();

        BigDecimal cashReceipts = cashSettlementModeIds.isEmpty() ? BigDecimal.ZERO
            : settlementLineRepo.sumCashModeForBranchDate(orgId, branchId, date, cashSettlementModeIds);
        BigDecimal cashExpenses = cashExpenseModeIds.isEmpty() ? BigDecimal.ZERO
            : expenseLineRepo.sumCashModeForBranchDate(orgId, branchId, date, cashExpenseModeIds);
        BigDecimal cashIn = cashDocumentRepo.sumForBranchDateAndDirection(orgId, branchId, date, CashDirection.IN);
        BigDecimal cashOut = cashDocumentRepo.sumForBranchDateAndDirection(orgId, branchId, date, CashDirection.OUT);

        BigDecimal computed = opening.amount()
            .add(cashReceipts).add(cashIn)
            .subtract(cashExpenses).subtract(cashOut);

        return new DrawerPosition(opening.amount(), opening.set(),
            cashReceipts, cashIn, cashExpenses, cashOut, computed);
    }

    /**
     * {@code computedPosition} for many branches on one date, in a fixed handful of queries
     * instead of ~8 per branch. Same formula as {@link #position}; used by the Owner dashboard
     * roll-up (cash in hand + the branch-comparison table), never for the Cash page itself.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> computedPositions(Long orgId, Collection<Long> branchIds, LocalDate date) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (branchIds == null || branchIds.isEmpty()) {
            return out;
        }

        // opening: the most recent close strictly before the date wins (rows come newest-first,
        // so the first one seen per branch is that branch's opening); otherwise the one-time
        // configured opening if it is effective on/before the date; otherwise zero.
        Map<Long, BigDecimal> opening = new HashMap<>();
        for (CashDayClose c : cashDayCloseRepo.findByOrgIdAndCloseDateLessThanOrderByCloseDateDesc(orgId, date)) {
            opening.putIfAbsent(c.getBranchId(), c.getCountedAmount());
        }
        for (BranchCashOpening o : branchCashOpeningRepo.findByOrgId(orgId)) {
            if (!opening.containsKey(o.getBranchId()) && !o.getOpeningDate().isAfter(date)) {
                opening.put(o.getBranchId(), o.getAmount());
            }
        }

        List<Long> cashSettlementModeIds = settlementModeRepo.findByOrgIdAndCashTrue(orgId)
            .stream().map(SettlementMode::getId).toList();
        List<Long> cashExpenseModeIds = expenseModeRepo.findByOrgIdAndCashTrue(orgId)
            .stream().map(ExpenseMode::getId).toList();

        Map<Long, BigDecimal> cashReceipts = cashSettlementModeIds.isEmpty() ? Map.of()
            : pairs(settlementLineRepo.sumCashModeByBranchForDate(orgId, date, cashSettlementModeIds));
        Map<Long, BigDecimal> cashExpenses = cashExpenseModeIds.isEmpty() ? Map.of()
            : pairs(expenseLineRepo.sumCashModeByBranchForDate(orgId, date, cashExpenseModeIds));
        Map<Long, BigDecimal> cashIn = pairs(cashDocumentRepo.sumByBranchForDateAndDirection(orgId, date, CashDirection.IN));
        Map<Long, BigDecimal> cashOut = pairs(cashDocumentRepo.sumByBranchForDateAndDirection(orgId, date, CashDirection.OUT));

        for (Long branchId : branchIds) {
            BigDecimal position = opening.getOrDefault(branchId, BigDecimal.ZERO)
                .add(cashReceipts.getOrDefault(branchId, BigDecimal.ZERO))
                .add(cashIn.getOrDefault(branchId, BigDecimal.ZERO))
                .subtract(cashExpenses.getOrDefault(branchId, BigDecimal.ZERO))
                .subtract(cashOut.getOrDefault(branchId, BigDecimal.ZERO));
            out.put(branchId, position);
        }
        return out;
    }

    private static Map<Long, BigDecimal> pairs(List<Object[]> rows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }
        return m;
    }

    /** The opening drawer amount for a branch on a date — see the class doc for the chain. */
    @Transactional(readOnly = true)
    public Opening openingFor(Long orgId, Long branchId, LocalDate date) {
        return cashDayCloseRepo
            .findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(orgId, branchId, date)
            .map(c -> new Opening(c.getCountedAmount(), true))
            .orElseGet(() -> branchCashOpeningRepo.findByOrgIdAndBranchId(orgId, branchId)
                .filter(o -> !o.getOpeningDate().isAfter(date))
                .map(o -> new Opening(o.getAmount(), true))
                .orElse(new Opening(BigDecimal.ZERO, false)));
    }

    /** An opening amount plus whether it came from a real close / configured opening (vs. defaulted to 0). */
    public record Opening(BigDecimal amount, boolean set) {
    }
}
