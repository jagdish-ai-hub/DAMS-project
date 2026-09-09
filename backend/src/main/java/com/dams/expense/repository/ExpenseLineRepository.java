package com.dams.expense.repository;

import com.dams.expense.entity.ExpenseLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpenseLineRepository extends JpaRepository<ExpenseLine, Long> {

    Optional<ExpenseLine> findByIdAndOrgId(Long id, Long orgId);

    Optional<ExpenseLine> findByOrgIdAndExpenseDocumentIdAndLineNo(Long orgId, Long expenseDocumentId, Integer lineNo);

    List<ExpenseLine> findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(Long orgId, Long expenseDocumentId);

    List<ExpenseLine> findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(Long orgId, List<Long> expenseDocumentIds);

    /** Highest line number currently on a document (0 if none) — for the next line_no. */
    @Query("select coalesce(max(l.lineNo), 0) from ExpenseLine l where l.expenseDocumentId = :docId")
    int maxLineNo(@Param("docId") Long expenseDocumentId);

    /**
     * {@code [branchId, Σ cash-mode expense]} for one date across the whole org — the batched,
     * group-by-branch form of {@link #sumCashModeForBranchDate} used by the drawer roll-up on
     * the Owner dashboard. Caller must pass a non-empty {@code cashModeIds}.
     */
    @Query("""
        select d.branchId, coalesce(sum(l.amount), 0)
        from ExpenseLine l, ExpenseDocument d
        where l.expenseDocumentId = d.id
          and d.orgId = :orgId
          and l.transactionDate = :date
          and l.expenseModeId in :cashModeIds
          and d.workflowStatus <> com.dams.expense.entity.ExpenseWorkflowStatus.DRAFT
          and d.workflowStatus <> com.dams.expense.entity.ExpenseWorkflowStatus.REJECTED
        group by d.branchId
        """)
    List<Object[]> sumCashModeByBranchForDate(@Param("orgId") Long orgId,
                                              @Param("date") LocalDate date,
                                              @Param("cashModeIds") Collection<Long> cashModeIds);

    /**
     * Σ cash-mode expense paid at a branch on a date — the "− cash-mode expenses" term of the
     * Cash-page drawer formula. Counts every non-DRAFT, non-REJECTED expense document (a
     * CLOSED expense's cash still left the drawer). Caller must pass a non-empty
     * {@code cashModeIds}.
     */
    @Query("""
        select coalesce(sum(l.amount), 0)
        from ExpenseLine l, ExpenseDocument d
        where l.expenseDocumentId = d.id
          and d.orgId = :orgId
          and d.branchId = :branchId
          and l.transactionDate = :date
          and l.expenseModeId in :cashModeIds
          and d.workflowStatus <> com.dams.expense.entity.ExpenseWorkflowStatus.DRAFT
          and d.workflowStatus <> com.dams.expense.entity.ExpenseWorkflowStatus.REJECTED
        """)
    BigDecimal sumCashModeForBranchDate(@Param("orgId") Long orgId,
                                        @Param("branchId") Long branchId,
                                        @Param("date") LocalDate date,
                                        @Param("cashModeIds") Collection<Long> cashModeIds);

    // ---- Owner dashboard (Stage 9): APPROVED or CLOSED expenses; date is the line's transaction_date ----

    @Query("""
        select coalesce(sum(l.amount), 0)
        from ExpenseLine l, ExpenseDocument d
        where l.expenseDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.APPROVED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.CLOSED)
          and l.transactionDate between :from and :to
          and (:branchId is null or d.branchId = :branchId)
        """)
    BigDecimal dashboardExpenses(@Param("orgId") Long orgId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("branchId") Long branchId);

    @Query("""
        select d.branchId, coalesce(sum(l.amount), 0)
        from ExpenseLine l, ExpenseDocument d
        where l.expenseDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.APPROVED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.CLOSED)
          and l.transactionDate between :from and :to
        group by d.branchId
        """)
    List<Object[]> dashboardExpensesByBranch(@Param("orgId") Long orgId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    @Query("""
        select d.expenseCategoryId, coalesce(sum(l.amount), 0)
        from ExpenseLine l, ExpenseDocument d
        where l.expenseDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.APPROVED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.CLOSED)
          and l.transactionDate between :from and :to
          and (:branchId is null or d.branchId = :branchId)
        group by d.expenseCategoryId
        """)
    List<Object[]> dashboardExpensesByCategory(@Param("orgId") Long orgId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to,
                                               @Param("branchId") Long branchId);

    @Query("""
        select l.transactionDate, coalesce(sum(l.amount), 0)
        from ExpenseLine l, ExpenseDocument d
        where l.expenseDocumentId = d.id
          and d.orgId = :orgId
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.APPROVED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.CLOSED)
          and l.transactionDate between :from and :to
          and (:branchId is null or d.branchId = :branchId)
        group by l.transactionDate
        """)
    List<Object[]> dashboardExpensesByDay(@Param("orgId") Long orgId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to,
                                          @Param("branchId") Long branchId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
