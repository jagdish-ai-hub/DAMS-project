package com.dams.expense.repository;

import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpenseDocumentRepository extends JpaRepository<ExpenseDocument, Long> {

    Optional<ExpenseDocument> findByIdAndOrgId(Long id, Long orgId);

    /** My Entries — the caller's own documents, newest first. */
    List<ExpenseDocument> findByOrgIdAndCreatedByOrderByCreatedAtDesc(Long orgId, Long createdBy, Limit limit);

    /** Universal search — a document number match resolves to its job card / customer. */
    List<ExpenseDocument> findByOrgIdAndDocumentNoIgnoreCase(Long orgId, String documentNo);

    List<ExpenseDocument> findByOrgIdAndDocumentNoContainingIgnoreCase(Long orgId, String fragment);

    /** Accountant review queue — documents in one workflow state within the caller's branches, oldest first. */
    List<ExpenseDocument> findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(
        Long orgId, ExpenseWorkflowStatus workflowStatus, Collection<Long> branchIds);

    /** Finance Manager queue — documents in one workflow state org-wide, oldest first. */
    List<ExpenseDocument> findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
        Long orgId, ExpenseWorkflowStatus workflowStatus);

    boolean existsByOrgIdAndDocumentNo(Long orgId, String documentNo);

    /** Owner dashboard — expenses still in the review pipeline (SUBMITTED / VERIFIED / QUERIED). */
    @Query("""
        select count(d) from ExpenseDocument d
        where d.orgId = :orgId
          and (:branchId is null or d.branchId = :branchId)
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.SUBMITTED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.VERIFIED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.QUERIED)
        """)
    long countPendingReview(@Param("orgId") Long orgId, @Param("branchId") Long branchId);

    /** {@code [branchId, count]} of pipeline expenses per branch — one query for the whole dashboard. */
    @Query("""
        select d.branchId, count(d) from ExpenseDocument d
        where d.orgId = :orgId
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.SUBMITTED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.VERIFIED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.QUERIED)
        group by d.branchId
        """)
    List<Object[]> countPendingReviewByBranch(@Param("orgId") Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
