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

    /** Cashier inbox (rev 57) — the caller's documents currently in one of these states. */
    List<ExpenseDocument> findByOrgIdAndCreatedByAndWorkflowStatusIn(Long orgId, Long createdBy,
        Collection<com.dams.expense.entity.ExpenseWorkflowStatus> statuses);

    /** Cashier inbox (rev 57) — the caller's drafts that have an FM pre-approval request in flight or answered. */
    List<ExpenseDocument> findByOrgIdAndCreatedByAndWorkflowStatusAndPreApprovalStatusIsNotNull(Long orgId, Long createdBy,
        com.dams.expense.entity.ExpenseWorkflowStatus status);

    /** Universal search — a document number match resolves to its job card / customer. */
    List<ExpenseDocument> findByOrgIdAndDocumentNoIgnoreCase(Long orgId, String documentNo);

    List<ExpenseDocument> findByOrgIdAndDocumentNoContainingIgnoreCase(Long orgId, String fragment);

    /** Export of an on-screen list (rev 67) — exactly these documents; the caller orders and branch-filters them. */
    List<ExpenseDocument> findByOrgIdAndIdIn(Long orgId, Collection<Long> ids);

    /** Accountant review queue — documents in one workflow state within the caller's branches, oldest first. */
    List<ExpenseDocument> findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(
        Long orgId, ExpenseWorkflowStatus workflowStatus, Collection<Long> branchIds);

    /**
     * Accountant review queue — documents in any of several workflow states within the
     * caller's branches, oldest first. Used to show SUBMITTED and FM_QUERIED together (rev 49).
     */
    List<ExpenseDocument> findByOrgIdAndWorkflowStatusInAndBranchIdInOrderBySubmittedAtAscIdAsc(
        Long orgId, Collection<ExpenseWorkflowStatus> workflowStatuses, Collection<Long> branchIds);

    /** Finance Manager queue — documents in one workflow state org-wide, oldest first. */
    List<ExpenseDocument> findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
        Long orgId, ExpenseWorkflowStatus workflowStatus);

    /** Accountant "Verified" overview — documents past SUBMITTED within the caller's branches, newest first. */
    List<ExpenseDocument> findByOrgIdAndWorkflowStatusInAndBranchIdInOrderBySubmittedAtDescIdDesc(
        Long orgId, Collection<ExpenseWorkflowStatus> workflowStatuses, Collection<Long> branchIds);

    /** Owner's Expenses page (rev 60) — every document not in the given state (DRAFT), org-wide, newest first. */
    List<ExpenseDocument> findByOrgIdAndWorkflowStatusNotOrderBySubmittedAtDescIdDesc(
        Long orgId, ExpenseWorkflowStatus workflowStatus, Limit limit);

    /**
     * Claims summary (rev 62) — expenses raised (submitted) in a window that are in a claim
     * status, or were closed as a claim. Drafts and rejected expenses are not claims yet / any more.
     * {@code claimStatusIds} must be non-empty (pass a dummy id when the org has none).
     */
    @Query("""
        select d from ExpenseDocument d
        where d.orgId = :orgId
          and (:branchId is null or d.branchId = :branchId)
          and d.submittedAt >= :from and d.submittedAt < :to
          and d.workflowStatus <> com.dams.expense.entity.ExpenseWorkflowStatus.DRAFT
          and d.workflowStatus <> com.dams.expense.entity.ExpenseWorkflowStatus.REJECTED
          and (d.businessStatusId in :claimStatusIds or d.claimFinalAmount is not null)
        """)
    List<ExpenseDocument> findClaimExpenses(@Param("orgId") Long orgId,
                                            @Param("branchId") Long branchId,
                                            @Param("from") java.time.Instant from,
                                            @Param("to") java.time.Instant to,
                                            @Param("claimStatusIds") Collection<Long> claimStatusIds);

    /** FM "recently closed claims" (rev 61) — expense claims the Finance Manager has closed, newest first. */
    List<ExpenseDocument> findByOrgIdAndClaimClosedAtIsNotNullOrderByClaimClosedAtDesc(Long orgId, Limit limit);

    /** Override Audit (rev 61) — Finance Manager claim closes whose final amount differed from the total. */
    List<ExpenseDocument> findByOrgIdAndClaimOverriddenTrueAndClaimClosedAtBetweenOrderByClaimClosedAtDesc(
        Long orgId, java.time.Instant from, java.time.Instant to);

    /** Customer history — every expense tagged to one of this customer's job cards, newest first. */
    List<ExpenseDocument> findByOrgIdAndJobCardIdInOrderByCreatedAtDesc(Long orgId, Collection<Long> jobCardIds);

    /** Customer history (rev 56) — expenses linked to the customer directly, with or without a job card. */
    List<ExpenseDocument> findByOrgIdAndCustomerIdOrderByCreatedAtDesc(Long orgId, Long customerId);

    boolean existsByOrgIdAndDocumentNo(Long orgId, String documentNo);

    boolean existsByOrgId(Long orgId);

    /** Owner dashboard — expenses still in the review pipeline (SUBMITTED / VERIFIED / QUERIED / FM_QUERIED). */
    @Query("""
        select count(d) from ExpenseDocument d
        where d.orgId = :orgId
          and (:branchId is null or d.branchId = :branchId)
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.SUBMITTED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.VERIFIED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.QUERIED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.FM_QUERIED)
        """)
    long countPendingReview(@Param("orgId") Long orgId, @Param("branchId") Long branchId);

    /** {@code [branchId, count]} of pipeline expenses per branch — one query for the whole dashboard. */
    @Query("""
        select d.branchId, count(d) from ExpenseDocument d
        where d.orgId = :orgId
          and d.workflowStatus in (com.dams.expense.entity.ExpenseWorkflowStatus.SUBMITTED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.VERIFIED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.QUERIED,
                                   com.dams.expense.entity.ExpenseWorkflowStatus.FM_QUERIED)
        group by d.branchId
        """)
    List<Object[]> countPendingReviewByBranch(@Param("orgId") Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);

    /** The FM's pending pre-approval requests (rev 53), oldest first. */
    List<ExpenseDocument> findByOrgIdAndPreApprovalStatusOrderByApprovalRequestedAtAscIdAsc(
        Long orgId, com.dams.expense.entity.PreApprovalStatus preApprovalStatus);

    /** Documents in any of {@code statuses}, optionally one branch — the Owner dashboard's "stuck with whom" card (rev 71). */
    @Query("""
        select d from ExpenseDocument d
        where d.orgId = :orgId
          and d.workflowStatus in :statuses
          and (:branchId is null or d.branchId = :branchId)
        order by d.submittedAt asc nulls last, d.createdAt asc, d.id asc
        """)
    List<ExpenseDocument> findForPendingWork(@Param("orgId") Long orgId,
                                             @Param("statuses") Collection<ExpenseWorkflowStatus> statuses,
                                             @Param("branchId") Long branchId);
}
