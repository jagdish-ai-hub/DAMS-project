package com.dams.receive.repository;

import com.dams.receive.entity.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {

    java.util.Optional<SettlementLine> findByIdAndOrgId(Long id, Long orgId);

    java.util.Optional<SettlementLine> findByOrgIdAndReceiveDocumentIdAndLineNo(Long orgId, Long receiveDocumentId, Integer lineNo);

    List<SettlementLine> findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(Long orgId, Long receiveDocumentId);

    List<SettlementLine> findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(Long orgId, List<Long> receiveDocumentIds);

    /**
     * Sum of every settlement line on a job card, across ALL of its receive documents except
     * REJECTED ones (a rejected document's lines are void). This is the Σ term of Pending
     * Amount — see {@link com.dams.jobcard.service.PendingAmountCalculator}.
     */
    @Query("""
        select coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.jobCardId = :jobCardId
          and d.workflowStatus <> com.dams.receive.entity.WorkflowStatus.REJECTED
        """)
    BigDecimal sumAmountForJobCard(@Param("orgId") Long orgId, @Param("jobCardId") Long jobCardId);

    /** Highest line number currently on a document (0 if none) — for the next line_no. */
    @Query("select coalesce(max(l.lineNo), 0) from SettlementLine l where l.receiveDocumentId = :docId")
    int maxLineNo(@Param("docId") Long receiveDocumentId);

    /**
     * {@code [jobCardId, Σ amount]} across every non-REJECTED receive document, for every job
     * card in the org — the batched form of {@link #sumAmountForJobCard} the Owner dashboard
     * uses so it does not fire one sum per job card.
     */
    @Query("""
        select d.jobCardId, coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus <> com.dams.receive.entity.WorkflowStatus.REJECTED
        group by d.jobCardId
        """)
    List<Object[]> sumAmountByJobCard(@Param("orgId") Long orgId);

    /**
     * {@code [branchId, Σ cash-mode settlement]} for one date across the whole org — the
     * batched, group-by-branch form of {@link #sumCashModeForBranchDate} used by the drawer
     * roll-up on the Owner dashboard. Caller must pass a non-empty {@code cashModeIds}.
     */
    @Query("""
        select d.branchId, coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and l.transactionDate = :date
          and l.settlementModeId in :cashModeIds
          and d.workflowStatus <> com.dams.receive.entity.WorkflowStatus.DRAFT
          and d.workflowStatus <> com.dams.receive.entity.WorkflowStatus.REJECTED
        group by d.branchId
        """)
    List<Object[]> sumCashModeByBranchForDate(@Param("orgId") Long orgId,
                                              @Param("date") java.time.LocalDate date,
                                              @Param("cashModeIds") java.util.Collection<Long> cashModeIds);

    /**
     * Σ cash-mode settlement received at a branch on a date — the "+ cash-mode receipts" term
     * of the Cash-page drawer formula. Counts every non-DRAFT, non-REJECTED receive document
     * (the cash is in the drawer regardless of review state). Caller must pass a non-empty
     * {@code cashModeIds}.
     */
    @Query("""
        select coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.branchId = :branchId
          and l.transactionDate = :date
          and l.settlementModeId in :cashModeIds
          and d.workflowStatus <> com.dams.receive.entity.WorkflowStatus.DRAFT
          and d.workflowStatus <> com.dams.receive.entity.WorkflowStatus.REJECTED
        """)
    BigDecimal sumCashModeForBranchDate(@Param("orgId") Long orgId,
                                        @Param("branchId") Long branchId,
                                        @Param("date") java.time.LocalDate date,
                                        @Param("cashModeIds") java.util.Collection<Long> cashModeIds);

    // ---- Owner dashboard (Stage 9): APPROVED receipts only; date is the line's transaction_date ----

    @Query("""
        select coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus = com.dams.receive.entity.WorkflowStatus.APPROVED
          and l.transactionDate between :from and :to
          and (:branchId is null or d.branchId = :branchId)
        """)
    BigDecimal dashboardCollections(@Param("orgId") Long orgId,
                                    @Param("from") java.time.LocalDate from,
                                    @Param("to") java.time.LocalDate to,
                                    @Param("branchId") Long branchId);

    @Query("""
        select d.branchId, coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus = com.dams.receive.entity.WorkflowStatus.APPROVED
          and l.transactionDate between :from and :to
        group by d.branchId
        """)
    List<Object[]> dashboardCollectionsByBranch(@Param("orgId") Long orgId,
                                                @Param("from") java.time.LocalDate from,
                                                @Param("to") java.time.LocalDate to);

    @Query("""
        select l.settlementModeId, coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus = com.dams.receive.entity.WorkflowStatus.APPROVED
          and l.transactionDate between :from and :to
          and (:branchId is null or d.branchId = :branchId)
        group by l.settlementModeId
        """)
    List<Object[]> dashboardCollectionsByMode(@Param("orgId") Long orgId,
                                              @Param("from") java.time.LocalDate from,
                                              @Param("to") java.time.LocalDate to,
                                              @Param("branchId") Long branchId);

    @Query("""
        select l.transactionDate, coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus = com.dams.receive.entity.WorkflowStatus.APPROVED
          and l.transactionDate between :from and :to
          and (:branchId is null or d.branchId = :branchId)
        group by l.transactionDate
        """)
    List<Object[]> dashboardCollectionsByDay(@Param("orgId") Long orgId,
                                             @Param("from") java.time.LocalDate from,
                                             @Param("to") java.time.LocalDate to,
                                             @Param("branchId") Long branchId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
