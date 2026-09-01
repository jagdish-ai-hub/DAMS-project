package com.dams.cash.repository;

import com.dams.cash.entity.CashDayClose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashDayCloseRepository extends JpaRepository<CashDayClose, Long> {

    Optional<CashDayClose> findByOrgIdAndBranchIdAndCloseDate(Long orgId, Long branchId, LocalDate closeDate);

    boolean existsByOrgIdAndBranchIdAndCloseDate(Long orgId, Long branchId, LocalDate closeDate);

    /** The branch's most recent close (its {@code closeDate} is the date lock; its {@code countedAmount} is the next opening). */
    Optional<CashDayClose> findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(Long orgId, Long branchId);

    /** The most recent close strictly before a date — the opening for that date. */
    Optional<CashDayClose> findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc(
        Long orgId, Long branchId, LocalDate date);

    List<CashDayClose> findByOrgIdAndBranchIdOrderByCloseDateDesc(Long orgId, Long branchId);

    /**
     * Every close in the org before a date, newest first — the batched form of
     * {@link #findFirstByOrgIdAndBranchIdAndCloseDateLessThanOrderByCloseDateDesc}: the drawer
     * roll-up takes the first row it sees per branch as that branch's opening.
     */
    List<CashDayClose> findByOrgIdAndCloseDateLessThanOrderByCloseDateDesc(Long orgId, LocalDate date);

    /** Every close in the org, newest first — the dashboard's per-branch "last closed" / variance. */
    List<CashDayClose> findByOrgIdOrderByCloseDateDesc(Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
