package com.dams.cash.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.entity.CashDayClose;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.common.exception.DamsException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The cash-day lock. Once a {@link CashDayClose} exists for a {@code (branch, date)}, nothing
 * dated on or before that date may still change the drawer for that branch:
 *
 *   - a new / re-submitted {@code cash_document} ({@link #requireCashDateOpen});
 *   - a <b>cash-mode</b> settlement or expense line — added, edited, or deleted
 *     ({@link #requireCashLineDateOpen}). A backdated cash payment into a closed day would
 *     silently make that day's {@code computed_closing} and the next day's opening wrong.
 *
 * Kept deliberately small — it depends only on {@link CashDayCloseRepository} (plus branch
 * lookup for the message), so {@code ReceiveDocumentService} / {@code ExpenseDocumentService}
 * can consult it without a service-level cycle (the drawer computation reads their
 * repositories, never their services).
 */
@Component
public class CashDateLock {

    private final CashDayCloseRepository cashDayCloseRepo;
    private final BranchRepository branchRepo;

    public CashDateLock(CashDayCloseRepository cashDayCloseRepo, BranchRepository branchRepo) {
        this.cashDayCloseRepo = cashDayCloseRepo;
        this.branchRepo = branchRepo;
    }

    /** A cash movement is inherently cash — always checked. */
    public void requireCashDateOpen(Long orgId, Long branchId, LocalDate transactionDate) {
        check(orgId, branchId, transactionDate, "A cash movement");
    }

    /** A settlement / expense line only affects the drawer when its mode is a cash mode. */
    public void requireCashLineDateOpen(Long orgId, Long branchId, LocalDate transactionDate,
                                        boolean modeIsCash, String lineKind) {
        if (!modeIsCash) {
            return;
        }
        check(orgId, branchId, transactionDate, "A cash " + lineKind + " line");
    }

    private void check(Long orgId, Long branchId, LocalDate transactionDate, String what) {
        LocalDate lockedThrough = cashDayCloseRepo
            .findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(orgId, branchId)
            .map(CashDayClose::getCloseDate)
            .orElse(null);
        if (lockedThrough != null && !transactionDate.isAfter(lockedThrough)) {
            String code = branchRepo.findByIdAndOrgId(branchId, orgId).map(Branch::getCode).orElse("?");
            throw DamsException.conflict(what + " dated " + transactionDate + " cannot be recorded — "
                + code + "'s cash is closed through " + lockedThrough
                + ". Closed days are locked so the drawer position can't change after the fact.");
        }
    }
}
