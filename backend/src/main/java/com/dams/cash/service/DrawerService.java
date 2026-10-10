package com.dams.cash.service;

import com.dams.cash.entity.CashDirection;
import com.dams.cash.repository.BranchCashOpeningRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.dashboard.dto.MoneyMovementItem;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dams.cash.entity.BranchCashOpening;
import com.dams.cash.entity.CashDayClose;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
 *
 * {@link #lineBreakdown} returns the actual settlement/expense lines behind the cash-mode
 * subtotals — the reconciliation drill-down the Cash page's drawer lines open into.
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
    private final ExpenseCategoryRepository expenseCategoryRepo;
    private final CustomerRepository customerRepo;
    private final ReceiverRepository receiverRepo;

    public DrawerService(BranchCashOpeningRepository branchCashOpeningRepo,
                         CashDayCloseRepository cashDayCloseRepo,
                         CashDocumentRepository cashDocumentRepo,
                         SettlementLineRepository settlementLineRepo,
                         ExpenseLineRepository expenseLineRepo,
                         SettlementModeRepository settlementModeRepo,
                         ExpenseModeRepository expenseModeRepo,
                         ExpenseCategoryRepository expenseCategoryRepo,
                         CustomerRepository customerRepo,
                         ReceiverRepository receiverRepo) {
        this.branchCashOpeningRepo = branchCashOpeningRepo;
        this.cashDayCloseRepo = cashDayCloseRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.expenseModeRepo = expenseModeRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
        this.customerRepo = customerRepo;
        this.receiverRepo = receiverRepo;
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

    /** The receipt lines and expense lines behind {@link DrawerPosition#cashReceipts()} / {@link DrawerPosition#cashExpenses()}. */
    public record DrawerLines(List<MoneyMovementItem> cashReceiptLines, List<MoneyMovementItem> cashExpenseLines) {
    }

    /**
     * Line-level detail behind the drawer's "cash receipts" / "cash expenses" subtotals for
     * one branch/date — same cash-mode + non-DRAFT/non-REJECTED filter as {@link #position}.
     */
    @Transactional(readOnly = true)
    public DrawerLines lineBreakdown(Long orgId, Long branchId, LocalDate date, String branchCode) {
        List<Long> cashSettlementModeIds = cashSettlementModeIds(orgId);
        List<Long> cashExpenseModeIds = cashExpenseModeIds(orgId);

        List<MoneyMovementItem> receiptLines = cashSettlementModeIds.isEmpty() ? List.of()
            : receiptItems(orgId, settlementLineRepo.findCashModeForBranchDate(orgId, branchId, date, cashSettlementModeIds), branchCode);
        List<MoneyMovementItem> expenseLines = cashExpenseModeIds.isEmpty() ? List.of()
            : expenseItems(orgId, expenseLineRepo.findCashModeForBranchDate(orgId, branchId, date, cashExpenseModeIds), branchCode);
        return new DrawerLines(receiptLines, expenseLines);
    }

    private List<Long> cashSettlementModeIds(Long orgId) {
        return settlementModeRepo.findByOrgIdAndCashTrue(orgId).stream().map(SettlementMode::getId).toList();
    }

    private List<Long> cashExpenseModeIds(Long orgId) {
        return expenseModeRepo.findByOrgIdAndCashTrue(orgId).stream().map(ExpenseMode::getId).toList();
    }

    /** {@code [line, document, jobCard]} rows → display items. */
    private List<MoneyMovementItem> receiptItems(Long orgId, List<Object[]> rows, String branchCode) {
        List<MoneyMovementItem> out = new ArrayList<>();
        if (rows.isEmpty()) {
            return out;
        }
        Map<Long, String> modeNames = settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementMode::getId, SettlementMode::getName));
        Map<Long, String> customerNames = customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, rows.stream()
                .map(r -> ((JobCard) r[2]).getCustomerId()).distinct().toList())
            .stream().collect(java.util.stream.Collectors.toMap(Customer::getId, Customer::getName));
        for (Object[] r : rows) {
            SettlementLine l = (SettlementLine) r[0];
            ReceiveDocument d = (ReceiveDocument) r[1];
            JobCard jc = (JobCard) r[2];
            out.add(new MoneyMovementItem("receipt", d.getId(), d.getDocumentNo(), d.getWorkflowStatus().name(),
                l.getTransactionDate(), l.getCreatedAt(), branchCode,
                jc.getCustomerId() == null ? "—" : customerNames.getOrDefault(jc.getCustomerId(), "—"),
                branchCode + "-JC-" + jc.getId(),
                modeNames.getOrDefault(l.getSettlementModeId(), "—"),
                l.getAmount()));
        }
        return out;
    }

    /** {@code [line, document]} rows → display items (amounts positive). */
    private List<MoneyMovementItem> expenseItems(Long orgId, List<Object[]> rows, String branchCode) {
        List<MoneyMovementItem> out = new ArrayList<>();
        if (rows.isEmpty()) {
            return out;
        }
        Map<Long, String> modeNames = expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(java.util.stream.Collectors.toMap(ExpenseMode::getId, ExpenseMode::getName));
        Map<Long, String> categoryNames = expenseCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(java.util.stream.Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName));
        Map<Long, String> receiverNames = receiverRepo.findByOrgIdAndIdIn(orgId, rows.stream()
                .map(r -> ((ExpenseDocument) r[1]).getReceiverId()).distinct().toList())
            .stream().collect(java.util.stream.Collectors.toMap(Receiver::getId, Receiver::getName));
        for (Object[] r : rows) {
            ExpenseLine l = (ExpenseLine) r[0];
            ExpenseDocument d = (ExpenseDocument) r[1];
            out.add(new MoneyMovementItem("expense", d.getId(), d.getDocumentNo(), d.getWorkflowStatus().name(),
                l.getTransactionDate(), l.getCreatedAt(), branchCode,
                receiverNames.getOrDefault(d.getReceiverId(), "—"),
                categoryNames.getOrDefault(d.getExpenseCategoryId(), "—"),
                modeNames.getOrDefault(l.getExpenseModeId(), "—"),
                l.getAmount()));
        }
        return out;
    }

    /**
     * A branch's running drawer position (Owner dashboard, rev 71). {@code from} is the first date
     * whose movement is included — the day after the last close, the configured opening's own
     * date, or {@code null} when the branch has neither (everything on record counts).
     */
    public record RunningPosition(LocalDate countedOn, BigDecimal opening, boolean openingSet,
                                  LocalDate from, BigDecimal movement, BigDecimal position) {
    }

    /**
     * Cash in hand as of {@code date}, per branch: the last closing count strictly before the
     * date (else the configured opening, else zero) PLUS every cash receipt / Cash In, minus every
     * cash expense / Cash Out dated after that close up to and including {@code date}. Unlike
     * the one-day {@link #position} it does not skip days that were never closed. Same
     * non-DRAFT / non-REJECTED rule as the drawer. A handful of queries for any number of branches.
     */
    @Transactional(readOnly = true)
    public Map<Long, RunningPosition> runningPositions(Long orgId, Collection<Long> branchIds, LocalDate date) {
        Map<Long, RunningPosition> out = new HashMap<>();
        if (branchIds == null || branchIds.isEmpty()) {
            return out;
        }
        Map<Long, CashDayClose> lastClose = new HashMap<>();
        for (CashDayClose c : cashDayCloseRepo.findByOrgIdAndCloseDateLessThanOrderByCloseDateDesc(orgId, date)) {
            lastClose.putIfAbsent(c.getBranchId(), c);   // newest first → first seen wins
        }
        Map<Long, BranchCashOpening> configured = new HashMap<>();
        for (BranchCashOpening o : branchCashOpeningRepo.findByOrgId(orgId)) {
            configured.put(o.getBranchId(), o);
        }

        List<Long> cashSettlementModeIds = cashSettlementModeIds(orgId);
        List<Long> cashExpenseModeIds = cashExpenseModeIds(orgId);
        Map<Long, Map<LocalDate, BigDecimal>> receipts = cashSettlementModeIds.isEmpty() ? Map.of()
            : perDay(settlementLineRepo.sumCashModeByBranchAndDateUpTo(orgId, date, cashSettlementModeIds));
        Map<Long, Map<LocalDate, BigDecimal>> expenses = cashExpenseModeIds.isEmpty() ? Map.of()
            : perDay(expenseLineRepo.sumCashModeByBranchAndDateUpTo(orgId, date, cashExpenseModeIds));
        Map<Long, Map<LocalDate, BigDecimal>> cashIn = perDay(cashDocumentRepo.sumByBranchAndDateUpTo(orgId, date, CashDirection.IN));
        Map<Long, Map<LocalDate, BigDecimal>> cashOut = perDay(cashDocumentRepo.sumByBranchAndDateUpTo(orgId, date, CashDirection.OUT));

        for (Long branchId : branchIds) {
            CashDayClose close = lastClose.get(branchId);
            BranchCashOpening opening = configured.get(branchId);
            LocalDate countedOn = null;
            LocalDate from = null;
            BigDecimal base = BigDecimal.ZERO;
            boolean set = false;
            if (close != null) {
                countedOn = close.getCloseDate();
                from = close.getCloseDate().plusDays(1);
                base = close.getCountedAmount();
                set = true;
            } else if (opening != null && !opening.getOpeningDate().isAfter(date)) {
                from = opening.getOpeningDate();
                base = opening.getAmount();
                set = true;
            }
            BigDecimal movement = since(receipts.get(branchId), from)
                .add(since(cashIn.get(branchId), from))
                .subtract(since(expenses.get(branchId), from))
                .subtract(since(cashOut.get(branchId), from));
            out.put(branchId, new RunningPosition(countedOn, base, set, from, movement, base.add(movement)));
        }
        return out;
    }

    /** Σ of a per-day map for dates on/after {@code from} ({@code null} = all). */
    private static BigDecimal since(Map<LocalDate, BigDecimal> byDay, LocalDate from) {
        if (byDay == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> e : byDay.entrySet()) {
            if (from == null || !e.getKey().isBefore(from)) {
                sum = sum.add(e.getValue());
            }
        }
        return sum;
    }

    /** {@code [branchId, date, amount]} rows → {@code branchId -> date -> amount}. */
    private static Map<Long, Map<LocalDate, BigDecimal>> perDay(List<Object[]> rows) {
        Map<Long, Map<LocalDate, BigDecimal>> m = new HashMap<>();
        for (Object[] r : rows) {
            m.computeIfAbsent(((Number) r[0]).longValue(), k -> new HashMap<>())
                .merge((LocalDate) r[1], (BigDecimal) r[2], BigDecimal::add);
        }
        return m;
    }

    /**
     * Every movement behind {@link #runningPositions} for one branch, newest first: an
     * "opening" row (the counted amount carried in), then cash receipts and Cash In as positive
     * amounts and cash expenses and Cash Out as NEGATIVE amounts — so the rows always sum to the
     * position. {@code kind} is receipt / expense / cash-in / cash-out / opening.
     */
    @Transactional(readOnly = true)
    public List<MoneyMovementItem> runningBreakdown(Long orgId, Long branchId, String branchCode, LocalDate date) {
        RunningPosition pos = runningPositions(orgId, List.of(branchId), date).get(branchId);
        LocalDate from = pos.from() == null ? LocalDate.of(2000, 1, 1) : pos.from();

        List<MoneyMovementItem> out = new ArrayList<>();
        List<Long> cashSettlementModeIds = cashSettlementModeIds(orgId);
        List<Long> cashExpenseModeIds = cashExpenseModeIds(orgId);
        if (!cashSettlementModeIds.isEmpty()) {
            out.addAll(receiptItems(orgId, settlementLineRepo.findCashModeForBranchRange(
                orgId, branchId, from, date, cashSettlementModeIds), branchCode));
        }
        if (!cashExpenseModeIds.isEmpty()) {
            for (MoneyMovementItem e : expenseItems(orgId, expenseLineRepo.findCashModeForBranchRange(
                    orgId, branchId, from, date, cashExpenseModeIds), branchCode)) {
                out.add(withAmount(e, e.amount().negate()));
            }
        }
        for (var c : cashDocumentRepo.findMovementsForBranchRange(orgId, branchId, from, date)) {
            boolean in = c.getDirection() == CashDirection.IN;
            out.add(new MoneyMovementItem(in ? "cash-in" : "cash-out", c.getId(), c.getDocumentNo(),
                c.getWorkflowStatus().name(), c.getTransactionDate(), c.getCreatedAt(), branchCode,
                in ? "Cash In from bank" : "Cash Out to bank",
                c.getRemark() == null || c.getRemark().isBlank() ? "Cash movement" : c.getRemark(),
                "Cash", in ? c.getAmount() : c.getAmount().negate()));
        }
        out.sort(java.util.Comparator.comparing(MoneyMovementItem::date).reversed());
        if (pos.openingSet()) {
            String where = pos.countedOn() != null ? "Counted at close of " + pos.countedOn() : "Opening balance";
            out.add(new MoneyMovementItem("opening", null, null, "OPENING",
                pos.countedOn() != null ? pos.countedOn() : pos.from(), null, branchCode,
                where, "Carried in", "Cash", pos.opening()));
        }
        return out;
    }

    private static MoneyMovementItem withAmount(MoneyMovementItem m, BigDecimal amount) {
        return new MoneyMovementItem(m.kind(), m.documentId(), m.documentNo(), m.workflowStatus(), m.date(),
            m.createdAt(), m.branchCode(), m.party(), m.description(), m.modeName(), amount);
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
